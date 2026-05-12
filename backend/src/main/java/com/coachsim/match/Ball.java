package com.coachsim.match;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "balls")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ball {

    public enum BowlerType { PACE, SPIN, MEDIUM }
    public enum BatterHand { LEFT, RIGHT }
    public enum OverPhase { POWERPLAY, MIDDLE, DEATH }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "innings_id", nullable = false)
    private Long inningsId;

    @Column(name = "over_num", nullable = false)
    private Short overNum;

    @Column(name = "ball_in_over", nullable = false)
    private Short ballInOver;

    @Column(nullable = false, length = 120)
    private String bowler;

    @Enumerated(EnumType.STRING)
    @Column(name = "bowler_type", length = 32)
    private BowlerType bowlerType;

    @Column(nullable = false, length = 120)
    private String batter;

    @Enumerated(EnumType.STRING)
    @Column(name = "batter_hand", length = 8)
    private BatterHand batterHand;

    @Column(nullable = false)
    private Short runs;

    @Column(nullable = false)
    private Short extras;

    @Column(nullable = false)
    private boolean wicket;

    @Column(name = "wicket_type", length = 32)
    private String wicketType;

    @Enumerated(EnumType.STRING)
    @Column(name = "over_phase", length = 16)
    private OverPhase overPhase;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (extras == null) extras = 0;
        if (runs == null) runs = 0;
    }

    public static OverPhase phaseForOver(int over) {
        if (over <= 6) return OverPhase.POWERPLAY;
        if (over <= 15) return OverPhase.MIDDLE;
        return OverPhase.DEATH;
    }
}
