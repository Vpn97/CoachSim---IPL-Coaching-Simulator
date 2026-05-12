package com.coachsim.decision;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "decision_scores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DecisionScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fan_decision_id", nullable = false, unique = true)
    private Long fanDecisionId;

    @Column(name = "captain_move_id")
    private Long captainMoveId;

    @Column(name = "merit_score", nullable = false)
    private Integer meritScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "breakdown_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> breakdown;

    @Column(name = "computed_at", nullable = false, updatable = false)
    private Instant computedAt;

    @PrePersist
    void onCreate() {
        if (computedAt == null) computedAt = Instant.now();
    }
}
