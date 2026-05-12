package com.coachsim.decision;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DecisionWindowRepository extends JpaRepository<DecisionWindow, Long> {

    Optional<DecisionWindow> findFirstByMatchIdAndStatusOrderByOpensAtAsc(Long matchId, DecisionWindow.Status status);

    List<DecisionWindow> findByMatchIdAndStatus(Long matchId, DecisionWindow.Status status);

    Optional<DecisionWindow> findByMatchIdAndTargetTypeAndTargetOverAndTargetBall(
            Long matchId, DecisionWindow.TargetType type, Short over, Short ball);
}
