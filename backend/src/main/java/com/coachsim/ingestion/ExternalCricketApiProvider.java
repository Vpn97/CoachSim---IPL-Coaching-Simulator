package com.coachsim.ingestion;

import com.coachsim.ingestion.cricapi.CricApiClient;
import com.coachsim.ingestion.cricapi.CricApiDto;
import com.coachsim.match.Ball;
import com.coachsim.match.Innings;
import com.coachsim.match.InningsRepository;
import com.coachsim.match.Match;
import com.coachsim.match.MatchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real-cricket-API adapter, currently backed by cricapi.com v2.
 *
 * <p>Two-step sync:
 * <ol>
 *   <li>{@code currentMatches} → upsert {@link Match} rows for IPL fixtures
 *       with {@code status=LIVE, source=EXTERNAL}.</li>
 *   <li>{@code match_scorecard} for each LIVE external match → derive coarse
 *       {@code BALL} events from innings-total deltas (CricAPI's free tier
 *       does not expose true ball-by-ball; for proper per-ball commentary use
 *       a paid vendor or trigger the auto-play simulator).</li>
 * </ol>
 *
 * <p>The provider is rate-limit-aware: it never throws out of the ingestion
 * loop and logs at WARN when the vendor returns an error envelope.
 */
@Component
public class ExternalCricketApiProvider implements MatchDataProvider {

    public static final String NAME = "external";

    private static final Logger log = LoggerFactory.getLogger(ExternalCricketApiProvider.class);

    private final MatchRepository matches;
    private final InningsRepository innings;
    private final CricApiClient client;
    private final boolean enabled;

    /** Last total runs we've seen per innings id (in-memory cursor — reset on restart). */
    private final Map<Long, Integer> lastRunsByInnings = new ConcurrentHashMap<>();

    public ExternalCricketApiProvider(MatchRepository matches,
                                      InningsRepository innings,
                                      ObjectMapper mapper,
                                      @Value("${app.external-api.base-url:}") String baseUrl,
                                      @Value("${app.external-api.api-key:}") String apiKey) {
        this.matches = matches;
        this.innings = innings;
        this.enabled = !baseUrl.isBlank() && !apiKey.isBlank();
        this.client = this.enabled ? new CricApiClient(baseUrl, apiKey, mapper) : null;
        if (!this.enabled) {
            log.info("External provider configured without base-url/api-key — it will be inert until configured");
        }
    }

    @Override
    public String name() { return NAME; }

    @Override
    @Transactional
    public List<Long> liveMatchIds() {
        if (!enabled) return List.of();

        // 1. Pull from vendor
        List<CricApiDto.MatchListItem> upstream = client.currentMatches();

        // 2. Filter to IPL — vendor often returns many tournaments, we want T20s with IPL teams
        upstream = upstream.stream()
                .filter(m -> "t20".equalsIgnoreCase(m.matchType())
                          || (m.name() != null && m.name().toUpperCase(Locale.ROOT).contains("IPL")))
                .toList();

        // 3. Upsert as LIVE / COMPLETED Match rows
        for (CricApiDto.MatchListItem item : upstream) {
            upsertMatch(item);
        }

        // 4. Return our internal ids for matches still LIVE
        return matches.findByStatusOrderByStartsAtAsc(Match.Status.LIVE).stream()
                .filter(m -> m.getSource() == Match.Source.EXTERNAL)
                .map(Match::getId)
                .toList();
    }

    @Override
    public List<MatchEvent> pollSince(long matchId, long lastBallOver, long lastBallInOver) {
        if (!enabled) return List.of();

        Match m = matches.findById(matchId).orElse(null);
        if (m == null || m.getExternalId() == null) return List.of();

        CricApiDto.MatchScorecard card = client.scorecard(m.getExternalId());
        if (card == null) return List.of();

        // Mark the match COMPLETED if the vendor indicates so via status text.
        if (card.status() != null && card.status().toLowerCase(Locale.ROOT).contains("won")) {
            m.setStatus(Match.Status.COMPLETED);
            matches.save(m);
            return List.of();
        }

        if (card.score() == null) return List.of();

        // Derive coarse BALL events per innings from total-runs deltas.
        List<MatchEvent> events = new ArrayList<>();
        List<Innings> ourInnings = innings.findByMatchIdOrderByNumberAsc(matchId);

        for (CricApiDto.ScoreLine line : card.score()) {
            Innings inn = matchInningsByTitle(ourInnings, line.inning());
            if (inn == null || line.r() == null || line.o() == null) continue;

            int prev = lastRunsByInnings.getOrDefault(inn.getId(), 0);
            int delta = line.r() - prev;
            if (delta <= 0) continue;

            // Approximate over/ball position from vendor's overs decimal (e.g. 4.3 -> over 5, ball 3+1)
            int overWhole = (int) Math.floor(line.o());
            int ballInOver = (int) Math.round((line.o() - overWhole) * 10);
            if (ballInOver < 1) ballInOver = 1;
            if (ballInOver > 6) ballInOver = 6;
            int overNum = overWhole + (ballInOver == 1 ? 1 : 1); // 1-based

            events.add(MatchEvent.builder()
                    .type(MatchEvent.Type.BALL)
                    .matchId(matchId)
                    .inningsNumber(inn.getNumber())
                    .overNum((short) overNum)
                    .ballInOver((short) ballInOver)
                    .ball(new MatchEvent.BallPayload(
                            currentBowler(card, inn.getNumber()), Ball.BowlerType.PACE,   // type unknown via free tier
                            "Batter (live)", Ball.BatterHand.RIGHT,
                            delta, 0, false, null))
                    .build());

            lastRunsByInnings.put(inn.getId(), line.r());
        }
        return events;
    }

    // ------------------------------------------------------------------ //

    private void upsertMatch(CricApiDto.MatchListItem item) {
        if (item.teams() == null || item.teams().size() < 2) return;

        Match existing = matches.findAll().stream()
                .filter(m -> item.id().equals(m.getExternalId()) && m.getSource() == Match.Source.EXTERNAL)
                .findFirst()
                .orElse(null);

        Match m = existing != null ? existing : new Match();
        m.setExternalId(item.id());
        m.setSeason(item.date() != null && item.date().length() >= 4 ? item.date().substring(0, 4) : "live");
        m.setHomeTeam(item.teams().get(0));
        m.setAwayTeam(item.teams().get(1));
        m.setVenue(item.venue());
        m.setSource(Match.Source.EXTERNAL);
        m.setStatus(Boolean.TRUE.equals(item.matchEnded()) ? Match.Status.COMPLETED
                  : Boolean.TRUE.equals(item.matchStarted()) ? Match.Status.LIVE
                  : Match.Status.SCHEDULED);
        m.setStartsAt(parseStartsAt(item.dateTimeGMT()));

        boolean isNew = existing == null;
        Match saved = matches.save(m);
        if (isNew && saved.getStatus() != Match.Status.SCHEDULED) {
            innings.save(Innings.builder().matchId(saved.getId()).number((short) 1)
                    .battingTeam(item.teams().get(0)).bowlingTeam(item.teams().get(1)).build());
            innings.save(Innings.builder().matchId(saved.getId()).number((short) 2)
                    .battingTeam(item.teams().get(1)).bowlingTeam(item.teams().get(0)).build());
        }
    }

    private Innings matchInningsByTitle(List<Innings> ours, String vendorTitle) {
        if (vendorTitle == null || ours.isEmpty()) return null;
        String upper = vendorTitle.toUpperCase(Locale.ROOT);
        if (upper.contains("INNING 2") || upper.contains("INNINGS 2")) {
            return ours.stream().filter(i -> i.getNumber() == 2).findFirst().orElse(null);
        }
        return ours.stream().filter(i -> i.getNumber() == 1).findFirst().orElse(null);
    }

    private String currentBowler(CricApiDto.MatchScorecard card, short inningsNumber) {
        if (card.scorecard() == null) return "Unknown";
        return card.scorecard().stream()
                .filter(c -> c.title() != null
                          && c.title().toUpperCase(Locale.ROOT).contains("INNING " + (3 - inningsNumber)))
                .findFirst()
                .map(c -> c.bowling() == null || c.bowling().isEmpty() ? null
                        : c.bowling().stream().max(Comparator.comparingDouble(b -> b.o() == null ? 0 : b.o()))
                        .map(CricApiDto.BowlingLine::bowler).orElse(null))
                .orElse("Unknown");
    }

    private Instant parseStartsAt(String s) {
        if (s == null || s.isBlank()) return Instant.now();
        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (DateTimeParseException ex) {
            return Instant.now();
        }
    }

    /** For unit tests / admin use to clear the in-memory cursor. */
    public void resetCursor() { lastRunsByInnings.clear(); }

    // Kept for clarity — the seed map is not currently used outside this class.
    @SuppressWarnings("unused")
    private static Map<String, Ball.BowlerType> bowlerTypeHints() { return new HashMap<>(); }
}
