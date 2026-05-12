package com.coachsim.ingestion;

import com.coachsim.decision.DecisionWindowService;
import com.coachsim.match.*;
import com.coachsim.realtime.MatchEventPublisher;
import io.micrometer.core.instrument.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * Persists incoming MatchEvents and fans them out to real-time subscribers.
 * Common logic — provider-agnostic.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final MatchRepository matches;
    private final InningsRepository innings;
    private final BallRepository balls;
    private final CaptainMoveRepository captainMoves;
    private final MatchEventPublisher publisher;
    private final DecisionWindowService windows;
    private final Counter ballsIngested;

    public IngestionService(MatchRepository matches,
                            InningsRepository innings,
                            BallRepository balls,
                            CaptainMoveRepository captainMoves,
                            MatchEventPublisher publisher,
                            DecisionWindowService windows,
                            @Qualifier("ballsIngested") Counter ballsIngested) {
        this.matches = matches;
        this.innings = innings;
        this.balls = balls;
        this.captainMoves = captainMoves;
        this.publisher = publisher;
        this.windows = windows;
        this.ballsIngested = ballsIngested;
    }

    @Transactional
    public void ingest(MatchEvent ev) {
        switch (ev.type()) {
            case BALL -> ingestBall(ev);
            case CAPTAIN_MOVE -> ingestCaptainMove(ev);
            case INNINGS_START, MATCH_START, MATCH_END -> publisher.publishMatchEvent(ev.matchId(),
                    Map.of("type", ev.type().name(), "at", Instant.now().toString()));
        }
    }

    private void ingestBall(MatchEvent ev) {
        Innings inn = innings.findByMatchIdAndNumber(ev.matchId(), ev.inningsNumber())
                .orElseThrow(() -> new IllegalStateException(
                        "No innings " + ev.inningsNumber() + " for match " + ev.matchId()));

        MatchEvent.BallPayload b = ev.ball();
        Ball ball = Ball.builder()
                .inningsId(inn.getId())
                .overNum(ev.overNum())
                .ballInOver(ev.ballInOver())
                .bowler(b.bowler())
                .bowlerType(b.bowlerType())
                .batter(b.batter())
                .batterHand(b.batterHand())
                .runs((short) b.runs())
                .extras((short) b.extras())
                .wicket(b.wicket())
                .wicketType(b.wicketType())
                .overPhase(Ball.phaseForOver(ev.overNum()))
                .build();
        balls.save(ball);
        ballsIngested.increment();

        log.debug("Ingested ball {} over {}.{} for match {}", ball.getId(), ev.overNum(), ev.ballInOver(), ev.matchId());

        publisher.publishMatchEvent(ev.matchId(), Map.of(
                "type", "BALL",
                "innings", ev.inningsNumber(),
                "over", ev.overNum(),
                "ball", ev.ballInOver(),
                "runs", b.runs(),
                "extras", b.extras(),
                "wicket", b.wicket(),
                "bowler", b.bowler(),
                "batter", b.batter()
        ));

        windows.openWindowForNextBall(ev.matchId(), ev.overNum(), ev.ballInOver());
    }

    private void ingestCaptainMove(MatchEvent ev) {
        CaptainMove move = CaptainMove.builder()
                .matchId(ev.matchId())
                .moveType(ev.captain().type())
                .beforeOver(ev.overNum())
                .beforeBall(ev.ballInOver())
                .payload(ev.captain().payload())
                .build();
        captainMoves.save(move);

        log.debug("Ingested captain move {} at {}.{} for match {}", move.getMoveType(), ev.overNum(), ev.ballInOver(), ev.matchId());

        publisher.publishMatchEvent(ev.matchId(), Map.of(
                "type", "CAPTAIN_MOVE",
                "moveType", move.getMoveType().name(),
                "over", ev.overNum(),
                "ball", ev.ballInOver(),
                "payload", move.getPayload()
        ));

        windows.resolveWindow(move);
    }
}
