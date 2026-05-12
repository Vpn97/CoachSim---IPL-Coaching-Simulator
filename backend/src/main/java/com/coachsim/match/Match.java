package com.coachsim.match;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.time.Instant;

@Entity
@Table(name = "matches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Match {

    public enum Status { SCHEDULED, LIVE, COMPLETED }
    public enum Source { MOCK, EXTERNAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id")
    private String externalId;

    @Column(nullable = false, length = 16)
    private String season;

    @Column(name = "home_team", nullable = false, length = 64)
    private String homeTeam;

    @Column(name = "away_team", nullable = false, length = 64)
    private String awayTeam;

    private String venue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Source source;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = Status.SCHEDULED;
        if (source == null) source = Source.MOCK;
    }
}
