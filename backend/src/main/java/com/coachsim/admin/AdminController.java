package com.coachsim.admin;

import com.coachsim.ingestion.IngestionService;
import com.coachsim.ingestion.MatchEvent;
import com.coachsim.ingestion.MockMatchDataProvider;
import com.coachsim.match.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

/**
 * Admin endpoints for the mock provider — create live matches, push synthetic
 * balls and captain moves to drive demos and integration tests.
 *
 * Everything here is gated by ROLE_ADMIN at the security layer + method level.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final MatchRepository matches;
    private final InningsRepository innings;
    private final MockMatchDataProvider mock;
    private final IngestionService ingestion;
    private final AutoPlaySimulator simulator;

    public AdminController(MatchRepository matches, InningsRepository innings,
                           MockMatchDataProvider mock, IngestionService ingestion,
                           AutoPlaySimulator simulator) {
        this.matches = matches;
        this.innings = innings;
        this.mock = mock;
        this.ingestion = ingestion;
        this.simulator = simulator;
    }

    public record CreateMatchRequest(
            @NotBlank String season,
            @NotBlank String homeTeam,
            @NotBlank String awayTeam,
            String venue
    ) {}

    public record BallRequest(
            @NotNull Long matchId,
            @NotNull Short inningsNumber,
            @NotNull Short over,
            @NotNull Short ball,
            @NotBlank String bowler,
            @NotBlank String bowlerType,   // PACE | SPIN | MEDIUM
            @NotBlank String batter,
            @NotBlank String batterHand,   // LEFT | RIGHT
            int runs,
            int extras,
            boolean wicket
    ) {}

    public record CaptainRequest(
            @NotNull Long matchId,
            @NotNull Short over,
            @NotNull Short ball,
            @NotBlank String moveType,       // BOWLING_CHANGE | FIELD_SET
            @NotNull Map<String, Object> payload
    ) {}

    @PostMapping("/matches")
    public ResponseEntity<Match> createMatch(@Valid @RequestBody CreateMatchRequest req) {
        Match m = matches.save(Match.builder()
                .season(req.season())
                .homeTeam(req.homeTeam())
                .awayTeam(req.awayTeam())
                .venue(req.venue())
                .status(Match.Status.LIVE)
                .source(Match.Source.MOCK)
                .startsAt(Instant.now())
                .build());
        innings.save(Innings.builder().matchId(m.getId()).number((short) 1)
                .battingTeam(req.homeTeam()).bowlingTeam(req.awayTeam()).build());
        innings.save(Innings.builder().matchId(m.getId()).number((short) 2)
                .battingTeam(req.awayTeam()).bowlingTeam(req.homeTeam()).build());
        return ResponseEntity.status(HttpStatus.CREATED).body(m);
    }

    @PostMapping("/matches/{id}/complete")
    public ResponseEntity<Void> complete(@PathVariable Long id) {
        Match m = matches.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        m.setStatus(Match.Status.COMPLETED);
        matches.save(m);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/balls")
    public ResponseEntity<Void> pushBall(@Valid @RequestBody BallRequest req) {
        MatchEvent ev = MatchEvent.builder()
                .type(MatchEvent.Type.BALL)
                .matchId(req.matchId())
                .inningsNumber(req.inningsNumber())
                .overNum(req.over())
                .ballInOver(req.ball())
                .ball(new MatchEvent.BallPayload(
                        req.bowler(), Ball.BowlerType.valueOf(req.bowlerType()),
                        req.batter(), Ball.BatterHand.valueOf(req.batterHand()),
                        req.runs(), req.extras(), req.wicket(), null))
                .build();
        mock.enqueue(req.matchId(), ev);
        ingestion.ingest(ev);
        return ResponseEntity.accepted().build();
    }

    public record AutoPlayRequest(Integer ballEverySeconds) {}

    public record AutoPlayStatus(Long matchId, boolean running) {}

    @PostMapping("/matches/{id}/auto-play/start")
    public ResponseEntity<AutoPlayStatus> startAutoPlay(@PathVariable Long id,
                                                        @RequestBody(required = false) AutoPlayRequest req) {
        int interval = req == null || req.ballEverySeconds() == null ? 5 : req.ballEverySeconds();
        try {
            simulator.start(id, interval);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
        return ResponseEntity.accepted().body(new AutoPlayStatus(id, true));
    }

    @PostMapping("/matches/{id}/auto-play/stop")
    public ResponseEntity<AutoPlayStatus> stopAutoPlay(@PathVariable Long id) {
        simulator.stop(id);
        return ResponseEntity.ok(new AutoPlayStatus(id, false));
    }

    @GetMapping("/matches/{id}/auto-play")
    public AutoPlayStatus autoPlayStatus(@PathVariable Long id) {
        return new AutoPlayStatus(id, simulator.isRunning(id));
    }

    /**
     * Bulk variant — used by the admin UI to render a "RUNNING" badge against
     * every LIVE match in one round trip, so a refresh / second-tab open
     * immediately shows which matches already have an auto-play loop active
     * (prevents accidentally starting a duplicate simulation).
     */
    @GetMapping("/auto-play")
    public Map<Long, Boolean> autoPlayStatusAll() {
        return matches.findByStatusOrderByStartsAtAsc(Match.Status.LIVE).stream()
                .collect(java.util.stream.Collectors.toMap(
                        Match::getId,
                        m -> simulator.isRunning(m.getId())));
    }

    @PostMapping("/captain-move")
    public ResponseEntity<Void> pushCaptainMove(@Valid @RequestBody CaptainRequest req) {
        MatchEvent ev = MatchEvent.builder()
                .type(MatchEvent.Type.CAPTAIN_MOVE)
                .matchId(req.matchId())
                .overNum(req.over())
                .ballInOver(req.ball())
                .captain(new MatchEvent.CaptainPayload(
                        CaptainMove.MoveType.valueOf(req.moveType()),
                        req.payload()))
                .build();
        ingestion.ingest(ev);
        return ResponseEntity.accepted().build();
    }
}
