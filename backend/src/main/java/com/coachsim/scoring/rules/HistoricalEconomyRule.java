package com.coachsim.scoring.rules;

import com.coachsim.match.Ball;
import com.coachsim.match.BallRepository;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Only applies to BOWLING_CHANGE decisions.
 * Compares historical economy of the fan's chosen bowler type vs the captain's
 * chosen bowler type for the current (phase, batter_hand) bucket.
 */
@Component
public class HistoricalEconomyRule implements Rule {

    private final BallRepository balls;

    public HistoricalEconomyRule(BallRepository balls) {
        this.balls = balls;
    }

    @Override
    public String id() { return "historical_economy"; }

    @Override
    public Verdict apply(Context ctx) {
        if (ctx.window().getTargetType() != com.coachsim.decision.DecisionWindow.TargetType.BOWLING_CHANGE) {
            return new Verdict(id(), 0, 0, "n/a for FIELD_SET");
        }

        Map<String, Object> fan = ctx.fanDecision().getPayload();
        Map<String, Object> capt = ctx.captainMove().getPayload();

        Ball.BowlerType fanType = parseBowler(fan.get("bowlerType"));
        Ball.BowlerType capType = parseBowler(capt.get("bowlerType"));
        Ball.BatterHand hand = parseHand(capt.get("batterHand"));
        Ball.OverPhase phase = Ball.phaseForOver(ctx.window().getTargetOver());

        if (fanType == null || capType == null || hand == null) {
            return new Verdict(id(), 0, 30, "Insufficient context for historical lookup");
        }

        double fanAvg = avg(fanType, hand, phase);
        double capAvg = avg(capType, hand, phase);

        if (fanType == capType) {
            return new Verdict(id(), 30, 30, "Matches captain's bowler type — no penalty");
        }
        if (fanAvg <= 0 || capAvg <= 0) {
            return new Verdict(id(), 15, 30, "Limited historical data — neutral");
        }
        if (fanAvg < capAvg) {
            // fan picked a tighter option historically
            double improvement = (capAvg - fanAvg) / capAvg;
            int pts = (int) Math.min(30, Math.round(improvement * 60));
            return new Verdict(id(), pts, 30,
                    String.format("Fan's bowler type avg %.2f rpb < captain %.2f rpb in %s vs %s",
                            fanAvg, capAvg, phase, hand));
        }
        return new Verdict(id(), 5, 30,
                String.format("Fan's bowler type avg %.2f rpb >= captain %.2f rpb",
                        fanAvg, capAvg));
    }

    private double avg(Ball.BowlerType bt, Ball.BatterHand bh, Ball.OverPhase p) {
        if (balls.countHistorical(bt, bh, p) == 0) return 0;
        return balls.avgRunsPerBall(bt, bh, p);
    }

    private Ball.BowlerType parseBowler(Object v) {
        try { return v == null ? null : Ball.BowlerType.valueOf(v.toString()); }
        catch (Exception e) { return null; }
    }

    private Ball.BatterHand parseHand(Object v) {
        try { return v == null ? null : Ball.BatterHand.valueOf(v.toString()); }
        catch (Exception e) { return null; }
    }
}
