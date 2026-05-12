package com.coachsim.scoring.rules;

import com.coachsim.decision.DecisionWindow;
import com.coachsim.decision.FanDecision;
import com.coachsim.match.CaptainMove;

public interface Rule {

    record Verdict(String name, int points, int maxPoints, String detail) {}

    record Context(DecisionWindow window, FanDecision fanDecision, CaptainMove captainMove) {}

    /** Stable rule id for breakdowns. */
    String id();

    /** Compute partial score for this rule. */
    Verdict apply(Context ctx);
}
