package com.coachsim.match;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchRepository extends JpaRepository<Match, Long> {
    List<Match> findByStatusOrderByStartsAtAsc(Match.Status status);
    List<Match> findBySeasonOrderByStartsAtDesc(String season);
}
