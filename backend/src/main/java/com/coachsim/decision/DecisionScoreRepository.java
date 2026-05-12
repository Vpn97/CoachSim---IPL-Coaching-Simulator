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

    /**
     * All of a user's scored decisions for one match, newest first.
     * Powers the per-match "running total" panel on the live page.
     */
    @Query("""
           SELECT s FROM DecisionScore s
           JOIN FanDecision d ON d.id = s.fanDecisionId
           JOIN DecisionWindow w ON w.id = d.windowId
           WHERE d.userId = :userId
             AND w.matchId = :matchId
           ORDER BY s.computedAt DESC
           """)
    List<DecisionScore> findForUserInMatch(@Param("userId") Long userId,
                                           @Param("matchId") Long matchId);
}
