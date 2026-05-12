package com.coachsim.leaderboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final LeaderboardService leaderboard;

    public LeaderboardController(LeaderboardService leaderboard) {
        this.leaderboard = leaderboard;
    }

    public record Entry(int rank, Long userId, String displayName,
                        long totalScore, int decisionsCount, Instant refreshedAt) {
        static Entry from(LeaderboardEntry l) {
            return new Entry(l.getRank(), l.getUserId(), l.getDisplayName(),
                    l.getTotalScore(), l.getDecisionsCount(), l.getRefreshedAt());
        }
    }

    @GetMapping("/alltime")
    public List<Entry> allTime() {
        return leaderboard.top(LeaderboardEntry.Scope.ALLTIME, "ALL").stream().map(Entry::from).toList();
    }

    @GetMapping("/season/{season}")
    public List<Entry> season(@PathVariable String season) {
        return leaderboard.top(LeaderboardEntry.Scope.SEASON, season).stream().map(Entry::from).toList();
    }

    @GetMapping("/match/{matchId}")
    public List<Entry> match(@PathVariable Long matchId) {
        return leaderboard.top(LeaderboardEntry.Scope.MATCH, String.valueOf(matchId)).stream().map(Entry::from).toList();
    }
}
