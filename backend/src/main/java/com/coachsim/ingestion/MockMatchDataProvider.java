package com.coachsim.ingestion;

import com.coachsim.match.Match;
import com.coachsim.match.MatchRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

/**
 * Mock provider — events are pushed in by the admin panel through {@link #enqueue(long, MatchEvent)}.
 * The IngestionScheduler then drains the per-match queue at its normal cadence.
 */
@Component
public class MockMatchDataProvider implements MatchDataProvider {

    public static final String NAME = "mock";

    private final MatchRepository matches;
    private final ConcurrentMap<Long, Queue<MatchEvent>> queues = new ConcurrentHashMap<>();

    public MockMatchDataProvider(MatchRepository matches) {
        this.matches = matches;
    }

    @Override
    public String name() { return NAME; }

    @Override
    public List<Long> liveMatchIds() {
        return matches.findByStatusOrderByStartsAtAsc(Match.Status.LIVE).stream()
                .map(Match::getId).toList();
    }

    @Override
    public List<MatchEvent> pollSince(long matchId, long lastBallOver, long lastBallInOver) {
        Queue<MatchEvent> q = queues.get(matchId);
        if (q == null || q.isEmpty()) return List.of();
        List<MatchEvent> drained = new ArrayList<>();
        MatchEvent next;
        while ((next = q.poll()) != null) drained.add(next);
        return drained;
    }

    public void enqueue(long matchId, MatchEvent event) {
        queues.computeIfAbsent(matchId, k -> new ConcurrentLinkedQueue<>()).add(event);
    }
}
