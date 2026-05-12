package com.coachsim.leaderboard;

import com.coachsim.match.Match;
import com.coachsim.match.MatchRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Refreshes the materialised {@code leaderboard_snapshot} table on a schedule.
 * Each ranking is computed via a single grouped query so it scales linearly with score volume.
 */
@Service
public class LeaderboardService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);

    private final MatchRepository matches;
    private final LeaderboardRepository leaderboard;
    @PersistenceContext private EntityManager em;

    public LeaderboardService(MatchRepository matches, LeaderboardRepository leaderboard) {
        this.matches = matches;
        this.leaderboard = leaderboard;
    }

    public List<LeaderboardEntry> top(LeaderboardEntry.Scope scope, String scopeRef) {
        return leaderboard.findByScopeAndScopeRefOrderByRankAsc(scope, scopeRef);
    }

    /** Refresh every minute — fine for MVP scale. */
    @Scheduled(fixedDelayString = "${app.leaderboard.refresh-ms:60000}")
    @Transactional
    public void refreshAll() {
        refreshScope(LeaderboardEntry.Scope.ALLTIME, "ALL", null, null);

        Set<String> seasons = new HashSet<>(em.createQuery(
                "SELECT DISTINCT m.season FROM Match m", String.class).getResultList());
        for (String season : seasons) {
            refreshScope(LeaderboardEntry.Scope.SEASON, season, "season", season);
        }

        List<Match> live = matches.findByStatusOrderByStartsAtAsc(Match.Status.LIVE);
        for (Match m : live) {
            refreshScope(LeaderboardEntry.Scope.MATCH, String.valueOf(m.getId()), "match", m.getId());
        }

        log.debug("Leaderboard refreshed: {} seasons + {} live matches", seasons.size(), live.size());
    }

    @Transactional
    public void refreshScope(LeaderboardEntry.Scope scope, String scopeRef,
                             String filterMode, Object filterValue) {

        String filter = switch (scope) {
            case ALLTIME -> "";
            case SEASON  -> " AND m.season = :filter ";
            case MATCH   -> " AND m.id = :filter ";
        };

        var query = em.createQuery("""
                SELECT u.id, u.displayName, SUM(s.meritScore), COUNT(s)
                FROM DecisionScore s
                JOIN FanDecision d ON d.id = s.fanDecisionId
                JOIN DecisionWindow w ON w.id = d.windowId
                JOIN Match m ON m.id = w.matchId
                JOIN com.coachsim.user.User u ON u.id = d.userId
                WHERE 1=1
                """ + filter + """
                GROUP BY u.id, u.displayName
                ORDER BY SUM(s.meritScore) DESC, COUNT(s) DESC
                """, Object[].class);
        if (filterValue != null) query.setParameter("filter", filterValue);

        List<Object[]> rows = query.setMaxResults(500).getResultList();

        leaderboard.deleteScope(scope, scopeRef);
        Instant now = Instant.now();
        int rank = 1;
        for (Object[] r : rows) {
            leaderboard.save(LeaderboardEntry.builder()
                    .scope(scope)
                    .scopeRef(scopeRef)
                    .userId((Long) r[0])
                    .displayName((String) r[1])
                    .totalScore(((Number) r[2]).longValue())
                    .decisionsCount(((Number) r[3]).intValue())
                    .rank(rank++)
                    .refreshedAt(now)
                    .build());
        }
    }
}
