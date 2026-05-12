package com.coachsim.match;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BallRepository extends JpaRepository<Ball, Long> {

    List<Ball> findByInningsIdOrderByOverNumAscBallInOverAsc(Long inningsId);

    Optional<Ball> findFirstByInningsIdOrderByOverNumDescBallInOverDesc(Long inningsId);

    /**
     * Wipes every ball recorded for an innings. Used by the auto-play
     * simulator's "replay" path so the scoreboard restarts at 0/0 over 1
     * instead of colliding with the unique (innings, over, ball) constraint.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Ball b WHERE b.inningsId = :inningsId")
    int deleteByInningsId(@Param("inningsId") Long inningsId);

    @Query("""
           SELECT COALESCE(AVG(b.runs + b.extras), 0)
           FROM Ball b
           WHERE b.bowlerType = :bowlerType
             AND b.batterHand = :batterHand
             AND b.overPhase  = :phase
           """)
    double avgRunsPerBall(@Param("bowlerType") Ball.BowlerType bowlerType,
                          @Param("batterHand") Ball.BatterHand batterHand,
                          @Param("phase") Ball.OverPhase phase);

    @Query("""
           SELECT COUNT(b) FROM Ball b
           WHERE b.bowlerType = :bowlerType
             AND b.batterHand = :batterHand
             AND b.overPhase  = :phase
           """)
    long countHistorical(@Param("bowlerType") Ball.BowlerType bowlerType,
                         @Param("batterHand") Ball.BatterHand batterHand,
                         @Param("phase") Ball.OverPhase phase);
}
