package com.coachsim.match;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchRepository matches;
    private final InningsRepository innings;
    private final BallRepository balls;

    public MatchController(MatchRepository matches, InningsRepository innings, BallRepository balls) {
        this.matches = matches;
        this.innings = innings;
        this.balls = balls;
    }

    public record MatchSummary(Long id, String season, String homeTeam, String awayTeam,
                               String venue, String status, String source, Instant startsAt) {
        static MatchSummary from(Match m) {
            return new MatchSummary(m.getId(), m.getSeason(), m.getHomeTeam(), m.getAwayTeam(),
                    m.getVenue(), m.getStatus().name(), m.getSource().name(), m.getStartsAt());
        }
    }

    public record BallView(int over, int ballInOver, String bowler, String batter,
                           int runs, int extras, boolean wicket, String phase) {
        static BallView from(Ball b) {
            return new BallView(b.getOverNum(), b.getBallInOver(), b.getBowler(), b.getBatter(),
                    b.getRuns(), b.getExtras(), b.isWicket(),
                    b.getOverPhase() != null ? b.getOverPhase().name() : null);
        }
    }

    public record InningsState(int number, String battingTeam, String bowlingTeam,
                               int totalRuns, int wickets, int legalBalls, BallView lastBall) {}

    public record LiveState(MatchSummary match, List<InningsState> innings) {}

    @GetMapping
    public List<MatchSummary> list() {
        return matches.findAll().stream().map(MatchSummary::from).toList();
    }

    @GetMapping("/live")
    public List<MatchSummary> live() {
        return matches.findByStatusOrderByStartsAtAsc(Match.Status.LIVE).stream()
                .map(MatchSummary::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<MatchSummary> byId(@PathVariable Long id) {
        return matches.findById(id).map(MatchSummary::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/state")
    public ResponseEntity<LiveState> state(@PathVariable Long id) {
        Optional<Match> mOpt = matches.findById(id);
        if (mOpt.isEmpty()) return ResponseEntity.notFound().build();

        List<Innings> inns = innings.findByMatchIdOrderByNumberAsc(id);
        List<InningsState> inningsStates = inns.stream().map(this::toInningsState).toList();

        return ResponseEntity.ok(new LiveState(MatchSummary.from(mOpt.get()), inningsStates));
    }

    private InningsState toInningsState(Innings inn) {
        List<Ball> bs = balls.findByInningsIdOrderByOverNumAscBallInOverAsc(inn.getId());
        int runs = bs.stream().mapToInt(b -> b.getRuns() + b.getExtras()).sum();
        int wickets = (int) bs.stream().filter(Ball::isWicket).count();
        int legal = (int) bs.stream().filter(b -> b.getExtras() == 0).count();
        BallView last = bs.isEmpty() ? null : BallView.from(bs.get(bs.size() - 1));
        return new InningsState(inn.getNumber(), inn.getBattingTeam(), inn.getBowlingTeam(),
                runs, wickets, legal, last);
    }
}
