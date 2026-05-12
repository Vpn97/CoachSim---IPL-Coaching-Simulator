package com.coachsim.ingestion;

import com.coachsim.match.Ball;
import com.coachsim.match.CaptainMove;
import lombok.Builder;

import java.util.Map;

/**
 * Normalised event emitted by any MatchDataProvider.
 * Internal model — decouples ingestion sources from the rest of the app.
 */
@Builder
public record MatchEvent(
        Type type,
        Long matchId,
        Short inningsNumber,
        Short overNum,
        Short ballInOver,
        BallPayload ball,
        CaptainPayload captain
) {
    public enum Type { BALL, CAPTAIN_MOVE, INNINGS_START, MATCH_START, MATCH_END }

    @Builder
    public record BallPayload(
            String bowler, Ball.BowlerType bowlerType,
            String batter, Ball.BatterHand batterHand,
            int runs, int extras, boolean wicket, String wicketType) {}

    @Builder
    public record CaptainPayload(
            CaptainMove.MoveType type,
            Map<String, Object> payload) {}
}
