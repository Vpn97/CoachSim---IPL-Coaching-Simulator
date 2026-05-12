package com.coachsim.decision;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FanDecisionRepository extends JpaRepository<FanDecision, Long> {

    Optional<FanDecision> findByUserIdAndWindowId(Long userId, Long windowId);

    List<FanDecision> findByWindowId(Long windowId);

    List<FanDecision> findByUserIdOrderBySubmittedAtDesc(Long userId);
}
