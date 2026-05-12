package com.coachsim.leaderboard;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "leaderboard_snapshot")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaderboardEntry {

    public enum Scope { MATCH, SEASON, ALLTIME }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Scope scope;

    @Column(name = "scope_ref", nullable = false, length = 64)
    private String scopeRef;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "total_score", nullable = false)
    private Long totalScore;

    @Column(name = "decisions_count", nullable = false)
    private Integer decisionsCount;

    @Column(name = "rank", nullable = false)
    private Integer rank;

    @Column(name = "refreshed_at", nullable = false)
    private Instant refreshedAt;
}
