package com.coachsim.decision;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DecisionWindowRepository extends JpaRepository<DecisionWindow, Long> {

    Optional<DecisionWindow> findFirstByMatchIdAndStatusOrderByOpensAtAsc(Long matchId, DecisionWindow.Status status);

    List<DecisionWindow> findByMatchIdAndStatus(Long matchId, DecisionWindow.Status status);

    Optional<DecisionWindow> findByMatchIdAndTargetTypeAndTargetOverAndTargetBall(
            Long matchId, DecisionWindow.TargetType type, Short over, Short ball);

    List<DecisionWindow> findByMatchId(Long matchId);

    /** Used by the autoplay reset flow to wipe windows after dependent rows are gone. */
    @Modifying
    @Transactional
    @Query("DELETE FROM DecisionWindow w WHERE w.matchId = :matchId")
    int deleteByMatchId(@Param("matchId") Long matchId);
}
