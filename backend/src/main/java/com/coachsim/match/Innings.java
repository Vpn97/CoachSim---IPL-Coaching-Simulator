package com.coachsim.match;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "innings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Innings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(nullable = false)
    private Short number;

    @Column(name = "batting_team", nullable = false, length = 64)
    private String battingTeam;

    @Column(name = "bowling_team", nullable = false, length = 64)
    private String bowlingTeam;
}
