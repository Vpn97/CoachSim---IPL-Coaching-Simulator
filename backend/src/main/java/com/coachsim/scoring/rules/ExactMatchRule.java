package com.coachsim.scoring.rules;

import com.coachsim.decision.DecisionWindow;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * Highest weight: did the fan match the captain's actual move?
 *  - BOWLING_CHANGE: exact bowler match (+50), bowler-type match only (+25)
 *  - FIELD_SET    : exact positions list match (+50), >=70% overlap (+25)
 */
@Component
public class ExactMatchRule implements Rule {

    @Override
    public String id() { return "exact_match"; }

    @Override
    public Verdict apply(Context ctx) {
        Map<String, Object> fan = ctx.fanDecision().getPayload();
        Map<String, Object> capt = ctx.captainMove().getPayload();
        return switch (ctx.window().getTargetType()) {
            case BOWLING_CHANGE -> scoreBowling(fan, capt);
            case FIELD_SET -> scoreField(fan, capt);
        };
    }

    private Verdict scoreBowling(Map<String, Object> fan, Map<String, Object> capt) {
        if (Objects.equals(fan.get("bowler"), capt.get("bowler"))) {
            return new Verdict(id(), 50, 50, "Exact same bowler as captain");
        }
        if (Objects.equals(fan.get("bowlerType"), capt.get("bowlerType"))) {
            return new Verdict(id(), 25, 50, "Same bowler type (different bowler)");
        }
        return new Verdict(id(), 0, 50, "Different bowler & type");
    }

    private Verdict scoreField(Map<String, Object> fan, Map<String, Object> capt) {
        Object fp = fan.get("positions"), cp = capt.get("positions");
        if (Objects.equals(fp, cp)) {
            return new Verdict(id(), 50, 50, "Exact same field placement");
        }
        if (!(fp instanceof java.util.List<?> fanList) || !(cp instanceof java.util.List<?> capList)) {
            return new Verdict(id(), 0, 50, "Unstructured field");
        }
        int matches = 0;
        int n = Math.min(fanList.size(), capList.size());
        for (int i = 0; i < n; i++) {
            if (Objects.equals(fanList.get(i), capList.get(i))) matches++;
        }
        double pct = capList.isEmpty() ? 0 : (double) matches / capList.size();
        if (pct >= 0.70) return new Verdict(id(), 25, 50, "Field overlaps captain >= 70% (" + Math.round(pct * 100) + "%)");
        return new Verdict(id(), (int) Math.round(pct * 25), 50, "Field overlap " + Math.round(pct * 100) + "%");
    }
}
