package com.coachsim.decision;

import com.coachsim.auth.AuthPrincipal;
import io.micrometer.core.instrument.Counter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/decisions")
public class DecisionController {

    private final DecisionWindowService windowService;
    private final DecisionWindowRepository windows;
    private final FanDecisionRepository fanDecisions;
    private final DecisionScoreRepository scores;
    private final Counter submittedCounter;

    public DecisionController(DecisionWindowService windowService,
                              DecisionWindowRepository windows,
                              FanDecisionRepository fanDecisions,
                              DecisionScoreRepository scores,
                              @Qualifier("fanDecisionsSubmitted") Counter fanDecisionsSubmitted) {
        this.windowService = windowService;
        this.windows = windows;
        this.fanDecisions = fanDecisions;
        this.scores = scores;
        this.submittedCounter = fanDecisionsSubmitted;
    }

    public record SubmitRequest(
            @NotNull Long windowId,
            @NotNull Map<String, Object> payload
    ) {}

    public record SubmitResponse(Long decisionId, Long windowId, Instant submittedAt) {}

    public record WindowView(Long id, Long matchId, String type, int over, int ball,
                             Instant opensAt, Instant closesAt, String status) {
        static WindowView from(DecisionWindow w) {
            return new WindowView(w.getId(), w.getMatchId(), w.getTargetType().name(),
                    w.getTargetOver(), w.getTargetBall(),
                    w.getOpensAt(), w.getClosesAt(), w.getStatus().name());
        }
    }

    public record HistoryEntry(Long decisionId, Long windowId, Long matchId,
                               String type, int over, int ball,
                               Integer score, Map<String, Object> breakdown,
                               Map<String, Object> myPayload,
                               Instant submittedAt) {}

    /**
     * Snapshot of a fan's running performance in a single match. Drives the
     * live-page "your tactical merit so far" banner. Pure aggregate over the
     * per-decision scores already produced after every captain move.
     */
    public record MyScoreResponse(
            Long matchId,
            long totalScore,
            int decisionsScored,
            int decisionsPending,   // submitted but window not yet resolved
            double averageScore,
            int bestScore,
            Integer lastScore,
            Map<String, Object> lastBreakdown,
            Instant lastScoredAt
    ) {}

    @PostMapping
    public ResponseEntity<SubmitResponse> submit(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @Valid @RequestBody SubmitRequest req) {
        DecisionWindow w;
        try {
            w = windowService.requireOpenWindow(req.windowId());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }

        // Validate payload structurally per target type.
        validatePayload(w.getTargetType(), req.payload());

        boolean isNew = fanDecisions.findByUserIdAndWindowId(principal.id(), w.getId()).isEmpty();
        FanDecision saved = fanDecisions.findByUserIdAndWindowId(principal.id(), w.getId())
                .map(existing -> {
                    existing.setPayload(req.payload());
                    return fanDecisions.save(existing);
                })
                .orElseGet(() -> fanDecisions.save(FanDecision.builder()
                        .userId(principal.id())
                        .windowId(w.getId())
                        .payload(req.payload())
                        .build()));

        if (isNew) submittedCounter.increment();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SubmitResponse(saved.getId(), w.getId(), saved.getSubmittedAt()));
    }

    @GetMapping("/windows/open")
    public List<WindowView> openWindowsForMatch(@RequestParam("matchId") Long matchId) {
        return windows.findByMatchIdAndStatus(matchId, DecisionWindow.Status.OPEN).stream()
                .map(WindowView::from).toList();
    }

    @GetMapping("/my-score")
    public MyScoreResponse myScore(@AuthenticationPrincipal AuthPrincipal principal,
                                   @RequestParam("matchId") Long matchId) {
        List<DecisionScore> mine = scores.findForUserInMatch(principal.id(), matchId);
        long total = mine.stream().mapToLong(DecisionScore::getMeritScore).sum();
        int count = mine.size();
        int best = mine.stream().mapToInt(DecisionScore::getMeritScore).max().orElse(0);
        double avg = count == 0 ? 0 : (double) total / count;
        DecisionScore latest = mine.isEmpty() ? null : mine.get(0);

        // Submitted decisions for this match whose window hasn't been resolved yet
        // (no DecisionScore row). Counted via a small in-memory diff to keep the
        // endpoint cheap — the working set per fan per match is tiny.
        List<FanDecision> myDecisions = fanDecisions.findByUserIdOrderBySubmittedAtDesc(principal.id());
        long submittedForMatch = myDecisions.stream()
                .filter(d -> windows.findById(d.getWindowId())
                        .map(w -> matchId.equals(w.getMatchId()))
                        .orElse(false))
                .count();
        int pending = Math.max(0, (int) submittedForMatch - count);

        return new MyScoreResponse(
                matchId,
                total,
                count,
                pending,
                avg,
                best,
                latest != null ? latest.getMeritScore() : null,
                latest != null ? latest.getBreakdown() : null,
                latest != null ? latest.getComputedAt() : null
        );
    }

    @GetMapping("/history")
    public List<HistoryEntry> myHistory(@AuthenticationPrincipal AuthPrincipal principal) {
        List<FanDecision> mine = fanDecisions.findByUserIdOrderBySubmittedAtDesc(principal.id());
        return mine.stream().map(d -> {
            var w = windows.findById(d.getWindowId()).orElse(null);
            var s = scores.findByFanDecisionId(d.getId()).orElse(null);
            return new HistoryEntry(
                    d.getId(), d.getWindowId(),
                    w != null ? w.getMatchId() : null,
                    w != null ? w.getTargetType().name() : null,
                    w != null ? w.getTargetOver() : 0,
                    w != null ? w.getTargetBall() : 0,
                    s != null ? s.getMeritScore() : null,
                    s != null ? s.getBreakdown() : null,
                    d.getPayload(),
                    d.getSubmittedAt()
            );
        }).toList();
    }

    private void validatePayload(DecisionWindow.TargetType type, Map<String, Object> p) {
        switch (type) {
            case BOWLING_CHANGE -> {
                if (!p.containsKey("bowler") || !p.containsKey("bowlerType")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Bowling change requires 'bowler' and 'bowlerType'");
                }
            }
            case FIELD_SET -> {
                Object positions = p.get("positions");
                // The new visual ground UI lets fans place any subset of fielders
                // (presets give 8, custom can go from 4 up to 9). We only insist
                // on a sensible minimum so empty submissions are still rejected.
                if (!(positions instanceof List<?> list) || list.size() < 4 || list.size() > 9) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Field set requires between 4 and 9 fielding positions");
                }
                long legSideOutsideCircle = list.stream()
                        .filter(o -> o instanceof Map<?, ?>)
                        .map(o -> (Map<?, ?>) o)
                        .filter(m -> Boolean.TRUE.equals(m.get("legSide")) && Boolean.FALSE.equals(m.get("insideCircle")))
                        .count();
                if (legSideOutsideCircle > 5) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Illegal field: max 5 fielders on leg side outside the inner ring");
                }
            }
        }
    }
}
