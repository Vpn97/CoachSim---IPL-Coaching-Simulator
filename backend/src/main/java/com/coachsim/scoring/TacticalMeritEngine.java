package com.coachsim.scoring;

import com.coachsim.decision.DecisionWindow;
import com.coachsim.decision.FanDecision;
import com.coachsim.match.CaptainMove;
import com.coachsim.match.CaptainMoveRepository;
import com.coachsim.scoring.rules.Rule;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rules-based scoring engine (v1).
 * Each {@link Rule} produces a verdict that is summed and clamped to 0..100.
 */
@Component
public class TacticalMeritEngine implements ScoringStrategy {

    private final List<Rule> rules;
    private final CaptainMoveRepository captainMoves;

    public TacticalMeritEngine(List<Rule> rules, CaptainMoveRepository captainMoves) {
        this.rules = rules;
        this.captainMoves = captainMoves;
    }

    @Override
    public MeritScore score(DecisionWindow window, FanDecision fanDecision, Long captainMoveId) {
        CaptainMove move = captainMoves.findById(captainMoveId)
                .orElseThrow(() -> new IllegalStateException("Captain move " + captainMoveId + " not found"));

        Rule.Context ctx = new Rule.Context(window, fanDecision, move);

        int total = 0;
        int totalMax = 0;
        List<Map<String, Object>> verdicts = new ArrayList<>();
        for (Rule rule : rules) {
            Rule.Verdict v = rule.apply(ctx);
            total += v.points();
            totalMax += v.maxPoints();
            verdicts.add(Map.of(
                    "rule", v.name(),
                    "points", v.points(),
                    "maxPoints", v.maxPoints(),
                    "detail", v.detail()
            ));
        }

        int normalised = totalMax == 0 ? 0 : (int) Math.round(100.0 * total / totalMax);
        normalised = Math.max(0, Math.min(100, normalised));

        Map<String, Object> breakdown = new HashMap<>();
        breakdown.put("totalPoints", total);
        breakdown.put("maxPoints", totalMax);
        breakdown.put("normalised", normalised);
        breakdown.put("rules", verdicts);

        return new MeritScore(normalised, breakdown);
    }
}
