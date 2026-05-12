package com.coachsim.scoring;

import com.coachsim.decision.DecisionWindow;
import com.coachsim.decision.FanDecision;

import java.util.Map;

/**
 * Strategy interface so the rules-based engine can later be swapped for an ML-backed one
 * (e.g. an XGBoost service called over HTTP) without changing the decision pipeline.
 */
public interface ScoringStrategy {

    /** 0-100 score plus a free-form explainability breakdown. */
    record MeritScore(int score, Map<String, Object> breakdown) {}

    MeritScore score(DecisionWindow window, FanDecision fanDecision, Long captainMoveId);
}
