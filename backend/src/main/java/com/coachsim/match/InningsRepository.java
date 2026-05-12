package com.coachsim.match;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InningsRepository extends JpaRepository<Innings, Long> {
    List<Innings> findByMatchIdOrderByNumberAsc(Long matchId);
    Optional<Innings> findByMatchIdAndNumber(Long matchId, Short number);
}
