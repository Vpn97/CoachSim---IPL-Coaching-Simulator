package com.coachsim.match;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CaptainMoveRepository extends JpaRepository<CaptainMove, Long> {

    List<CaptainMove> findByMatchIdOrderByBeforeOverAscBeforeBallAsc(Long matchId);

    Optional<CaptainMove> findFirstByMatchIdAndMoveTypeAndBeforeOverAndBeforeBall(
            Long matchId, CaptainMove.MoveType type, Short over, Short ball);
}
