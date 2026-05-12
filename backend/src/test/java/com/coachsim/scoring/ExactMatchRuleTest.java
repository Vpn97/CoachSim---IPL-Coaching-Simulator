package com.coachsim.scoring;

import com.coachsim.decision.DecisionWindow;
import com.coachsim.decision.FanDecision;
import com.coachsim.match.CaptainMove;
import com.coachsim.scoring.rules.ExactMatchRule;
import com.coachsim.scoring.rules.Rule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExactMatchRuleTest {

    private final ExactMatchRule rule = new ExactMatchRule();

    @Test
    void bowlingChange_exactBowler_awards50() {
        var fan = decision(Map.of("bowler", "J. Bumrah", "bowlerType", "PACE"));
        var capt = move(CaptainMove.MoveType.BOWLING_CHANGE,
                Map.of("bowler", "J. Bumrah", "bowlerType", "PACE"));
        var win = window(DecisionWindow.TargetType.BOWLING_CHANGE);

        Rule.Verdict v = rule.apply(new Rule.Context(win, fan, capt));

        assertThat(v.points()).isEqualTo(50);
        assertThat(v.maxPoints()).isEqualTo(50);
    }

    @Test
    void bowlingChange_sameTypeOnly_awards25() {
        var fan = decision(Map.of("bowler", "T. Boult", "bowlerType", "PACE"));
        var capt = move(CaptainMove.MoveType.BOWLING_CHANGE,
                Map.of("bowler", "J. Bumrah", "bowlerType", "PACE"));
        var win = window(DecisionWindow.TargetType.BOWLING_CHANGE);

        assertThat(rule.apply(new Rule.Context(win, fan, capt)).points()).isEqualTo(25);
    }

    @Test
    void bowlingChange_differentBowlerAndType_awardsZero() {
        var fan = decision(Map.of("bowler", "Y. Chahal", "bowlerType", "SPIN"));
        var capt = move(CaptainMove.MoveType.BOWLING_CHANGE,
                Map.of("bowler", "J. Bumrah", "bowlerType", "PACE"));
        var win = window(DecisionWindow.TargetType.BOWLING_CHANGE);

        assertThat(rule.apply(new Rule.Context(win, fan, capt)).points()).isZero();
    }

    @Test
    void fieldSet_exactMatch_awards50() {
        var positions = List.of(
                Map.of("slot", 1, "zone", "COVERS"),
                Map.of("slot", 2, "zone", "POINT")
        );
        var fan = decision(Map.of("positions", positions));
        var capt = move(CaptainMove.MoveType.FIELD_SET, Map.of("positions", positions));
        var win = window(DecisionWindow.TargetType.FIELD_SET);

        assertThat(rule.apply(new Rule.Context(win, fan, capt)).points()).isEqualTo(50);
    }

    private FanDecision decision(Map<String, Object> payload) {
        return FanDecision.builder().userId(1L).windowId(1L).payload(payload).build();
    }

    private CaptainMove move(CaptainMove.MoveType type, Map<String, Object> payload) {
        return CaptainMove.builder().matchId(1L).moveType(type).beforeOver((short) 1).beforeBall((short) 1)
                .payload(payload).build();
    }

    private DecisionWindow window(DecisionWindow.TargetType type) {
        return DecisionWindow.builder().matchId(1L).targetType(type)
                .targetOver((short) 1).targetBall((short) 1).build();
    }
}
