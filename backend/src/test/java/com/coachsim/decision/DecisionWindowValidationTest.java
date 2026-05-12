package com.coachsim.decision;

import com.coachsim.realtime.MatchEventPublisher;
import com.coachsim.scoring.ScoringStrategy;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DecisionWindowValidationTest {

    private DecisionWindowService buildService(DecisionWindowRepository windows) {
        Counter counter = new SimpleMeterRegistry().counter("test");
        return new DecisionWindowService(
                windows,
                Mockito.mock(FanDecisionRepository.class),
                Mockito.mock(DecisionScoreRepository.class),
                Mockito.mock(ScoringStrategy.class),
                Mockito.mock(MatchEventPublisher.class),
                counter,
                15);
    }

    @Test
    void requireOpenWindow_throwsForClosedWindow() {
        DecisionWindowRepository windows = Mockito.mock(DecisionWindowRepository.class);
        DecisionWindow w = DecisionWindow.builder()
                .id(1L).matchId(1L).targetType(DecisionWindow.TargetType.BOWLING_CHANGE)
                .targetOver((short) 1).targetBall((short) 1)
                .opensAt(Instant.now().minusSeconds(60))
                .closesAt(Instant.now().minusSeconds(30))
                .status(DecisionWindow.Status.CLOSED)
                .build();
        Mockito.when(windows.findById(1L)).thenReturn(Optional.of(w));

        assertThatThrownBy(() -> buildService(windows).requireOpenWindow(1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requireOpenWindow_throwsForUnknownWindow() {
        DecisionWindowRepository windows = Mockito.mock(DecisionWindowRepository.class);
        Mockito.when(windows.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> buildService(windows).requireOpenWindow(99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireOpenWindow_expiredOpenWindow_isClosed() {
        DecisionWindowRepository windows = Mockito.mock(DecisionWindowRepository.class);
        DecisionWindow w = DecisionWindow.builder()
                .id(2L).matchId(1L).targetType(DecisionWindow.TargetType.BOWLING_CHANGE)
                .targetOver((short) 1).targetBall((short) 1)
                .opensAt(Instant.now().minusSeconds(60))
                .closesAt(Instant.now().minusSeconds(10))
                .status(DecisionWindow.Status.OPEN)
                .build();
        Mockito.when(windows.findById(2L)).thenReturn(Optional.of(w));

        assertThatThrownBy(() -> buildService(windows).requireOpenWindow(2L))
                .isInstanceOf(IllegalStateException.class);
    }
}
