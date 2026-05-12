package com.coachsim.scoring;

import com.coachsim.decision.DecisionWindow;
import com.coachsim.decision.FanDecision;
import com.coachsim.match.CaptainMove;
import com.coachsim.scoring.rules.FieldCoverageRule;
import com.coachsim.scoring.rules.Rule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FieldCoverageRuleTest {

    private final FieldCoverageRule rule = new FieldCoverageRule();

    @Test
    void notApplicableToBowlingChange() {
        var v = rule.apply(new Rule.Context(
                window(DecisionWindow.TargetType.BOWLING_CHANGE),
                FanDecision.builder().payload(Map.of()).build(),
                CaptainMove.builder().payload(Map.of()).build()
        ));
        assertThat(v.maxPoints()).isZero();
    }

    @Test
    void coversAllTopZones_awardsFull20() {
        var positions = List.of(
                Map.of("zone", "COVERS"),
                Map.of("zone", "POINT"),
                Map.of("zone", "MID_WICKET")
        );
        var fan = FanDecision.builder().payload(Map.of("positions", positions)).build();
        var capt = CaptainMove.builder()
                .payload(Map.of("batterTopZones", List.of("COVERS", "POINT", "MID_WICKET")))
                .build();

        Rule.Verdict v = rule.apply(new Rule.Context(
                window(DecisionWindow.TargetType.FIELD_SET), fan, capt));

        assertThat(v.points()).isEqualTo(20);
    }

    @Test
    void noOverlap_awardsZero() {
        var positions = List.of(Map.of("zone", "MID_OFF"));
        var fan = FanDecision.builder().payload(Map.of("positions", positions)).build();
        var capt = CaptainMove.builder()
                .payload(Map.of("batterTopZones", List.of("FINE_LEG")))
                .build();

        assertThat(rule.apply(new Rule.Context(window(DecisionWindow.TargetType.FIELD_SET), fan, capt))
                .points()).isZero();
    }

    private DecisionWindow window(DecisionWindow.TargetType type) {
        return DecisionWindow.builder().matchId(1L).targetType(type)
                .targetOver((short) 1).targetBall((short) 1).build();
    }
}
