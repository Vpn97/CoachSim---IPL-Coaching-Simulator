package com.coachsim.decision;

import com.coachsim.match.CaptainMove;
import com.coachsim.realtime.MatchEventPublisher;
import com.coachsim.scoring.ScoringStrategy;
import com.coachsim.scoring.ScoringStrategy.MeritScore;
import io.micrometer.core.instrument.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Opens decision windows after each ball and resolves them when the
 * matching captain move is ingested.
 */
@Service
public class DecisionWindowService {

    private static final Logger log = LoggerFactory.getLogger(DecisionWindowService.class);

    private final DecisionWindowRepository windows;
    private final FanDecisionRepository fanDecisions;
    private final DecisionScoreRepository scores;
    private final ScoringStrategy scoring;
    private final MatchEventPublisher publisher;
    private final Counter scoredCounter;
    private final int windowSeconds;

    public DecisionWindowService(DecisionWindowRepository windows,
                                 FanDecisionRepository fanDecisions,
                                 DecisionScoreRepository scores,
                                 ScoringStrategy scoring,
                                 MatchEventPublisher publisher,
                                 @Qualifier("fanDecisionsScored") Counter fanDecisionsScored,
                                 @Value("${app.decision.window-seconds}") int windowSeconds) {
        this.windows = windows;
        this.fanDecisions = fanDecisions;
        this.scores = scores;
        this.scoring = scoring;
        this.publisher = publisher;
        this.scoredCounter = fanDecisionsScored;
        this.windowSeconds = windowSeconds;
    }

    @Transactional
    public void openWindowForNextBall(Long matchId, short lastOver, short lastBall) {
        short nextOver = lastOver;
        short nextBall = (short) (lastBall + 1);
        if (nextBall > 6) { nextOver = (short) (lastOver + 1); nextBall = 1; }

        for (DecisionWindow.TargetType type : DecisionWindow.TargetType.values()) {
            var existing = windows.findByMatchIdAndTargetTypeAndTargetOverAndTargetBall(
                    matchId, type, nextOver, nextBall);
            if (existing.isPresent()) continue;

            Instant now = Instant.now();
            DecisionWindow w = DecisionWindow.builder()
                    .matchId(matchId)
                    .targetType(type)
                    .targetOver(nextOver)
                    .targetBall(nextBall)
                    .opensAt(now)
                    .closesAt(now.plusSeconds(windowSeconds))
                    .status(DecisionWindow.Status.OPEN)
                    .build();
            windows.save(w);

            publisher.publishDecisionWindow(matchId, Map.of(
                    "windowId", w.getId(),
                    "type", type.name(),
                    "over", nextOver,
                    "ball", nextBall,
                    "opensAt", w.getOpensAt().toString(),
                    "closesAt", w.getClosesAt().toString(),
                    "secondsRemaining", windowSeconds
            ));
        }
    }

    @Transactional
    public void resolveWindow(CaptainMove move) {
        var winOpt = windows.findByMatchIdAndTargetTypeAndTargetOverAndTargetBall(
                move.getMatchId(),
                DecisionWindow.TargetType.valueOf(move.getMoveType().name()),
                move.getBeforeOver(),
                move.getBeforeBall());
        if (winOpt.isEmpty()) {
            log.debug("No window to resolve for captain move {} at {}.{}", move.getMoveType(), move.getBeforeOver(), move.getBeforeBall());
            return;
        }
        DecisionWindow win = winOpt.get();
        win.setStatus(DecisionWindow.Status.CLOSED);
        win.setCaptainMoveId(move.getId());
        windows.save(win);

        scoreWindowAsync(win.getId(), move.getId());
    }

    @Async
    public void scoreWindowAsync(Long windowId, Long captainMoveId) {
        DecisionWindow win = windows.findById(windowId).orElse(null);
        if (win == null) return;
        List<FanDecision> decisions = fanDecisions.findByWindowId(windowId);
        for (FanDecision d : decisions) {
            try {
                // Idempotency guard: a decision_score is unique per fan_decision.
                // In the auto-play "replay" demo flow the same captain move can
                // be ingested again for an already-resolved window, so we skip
                // re-scoring instead of letting the unique constraint blow up
                // (which used to corrupt the autoplay tick transaction and
                // surface as "Transaction silently rolled back" errors).
                if (scores.findByFanDecisionId(d.getId()).isPresent()) {
                    log.debug("Decision {} already scored — skipping re-score", d.getId());
                    continue;
                }

                MeritScore score = scoring.score(win, d, captainMoveId);
                DecisionScore saved = scores.save(DecisionScore.builder()
                        .fanDecisionId(d.getId())
                        .captainMoveId(captainMoveId)
                        .meritScore(score.score())
                        .breakdown(score.breakdown())
                        .build());
                scoredCounter.increment();

                publisher.publishDecisionResult(String.valueOf(d.getUserId()), Map.of(
                        "windowId", windowId,
                        "score", saved.getMeritScore(),
                        "breakdown", saved.getBreakdown(),
                        "captainMoveId", captainMoveId
                ));
            } catch (Exception ex) {
                log.error("Scoring failed for decision {}: {}", d.getId(), ex.getMessage(), ex);
            }
        }
        win.setStatus(DecisionWindow.Status.RESOLVED);
        windows.save(win);
    }

    public DecisionWindow requireOpenWindow(Long windowId) {
        DecisionWindow w = windows.findById(windowId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown window " + windowId));
        if (w.getStatus() != DecisionWindow.Status.OPEN) {
            throw new IllegalStateException("Window " + windowId + " is " + w.getStatus());
        }
        if (Instant.now().isAfter(w.getClosesAt())) {
            w.setStatus(DecisionWindow.Status.CLOSED);
            windows.save(w);
            throw new IllegalStateException("Window " + windowId + " closed " +
                    Duration.between(w.getClosesAt(), Instant.now()).toSeconds() + "s ago");
        }
        return w;
    }
}
