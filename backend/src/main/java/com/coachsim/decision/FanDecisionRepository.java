package com.coachsim.decision;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FanDecisionRepository extends JpaRepository<FanDecision, Long> {

    Optional<FanDecision> findByUserIdAndWindowId(Long userId, Long windowId);

    List<FanDecision> findByWindowId(Long windowId);

    List<FanDecision> findByUserIdOrderBySubmittedAtDesc(Long userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM FanDecision d WHERE d.windowId IN :windowIds")
    int deleteByWindowIdIn(@Param("windowIds") Collection<Long> windowIds);
}
