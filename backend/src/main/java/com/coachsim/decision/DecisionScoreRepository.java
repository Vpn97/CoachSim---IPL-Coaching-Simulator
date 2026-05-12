package com.coachsim.decision;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DecisionScoreRepository extends JpaRepository<DecisionScore, Long> {

    Optional<DecisionScore> findByFanDecisionId(Long fanDecisionId);

    @Query("""
           SELECT s FROM DecisionScore s
           JOIN FanDecision d ON d.id = s.fanDecisionId
           WHERE d.userId = :userId
           ORDER BY s.computedAt DESC
           """)
    List<DecisionScore> findRecentForUser(@Param("userId") Long userId);
}
