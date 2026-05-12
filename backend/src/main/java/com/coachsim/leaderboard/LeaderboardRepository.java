package com.coachsim.leaderboard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LeaderboardRepository extends JpaRepository<LeaderboardEntry, Long> {

    List<LeaderboardEntry> findByScopeAndScopeRefOrderByRankAsc(LeaderboardEntry.Scope scope, String scopeRef);

    @Modifying
    @Query("DELETE FROM LeaderboardEntry l WHERE l.scope = :scope AND l.scopeRef = :scopeRef")
    void deleteScope(@Param("scope") LeaderboardEntry.Scope scope, @Param("scopeRef") String scopeRef);
}
