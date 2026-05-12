package com.coachsim.ingestion;

import com.coachsim.match.Ball;
import com.coachsim.match.BallRepository;
import com.coachsim.match.InningsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Picks the active provider from {@code app.ingestion.provider} and polls it
 * every {@code app.ingestion.poll-interval-ms} ms.
 */
@Component
public class IngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(IngestionScheduler.class);

    private final Map<String, MatchDataProvider> providersByName;
    private final IngestionService ingestion;
    private final InningsRepository innings;
    private final BallRepository balls;
    private final String activeProviderName;

    public IngestionScheduler(List<MatchDataProvider> providers,
                              IngestionService ingestion,
                              InningsRepository innings,
                              BallRepository balls,
                              @Value("${app.ingestion.provider}") String activeProviderName) {
        this.providersByName = providers.stream()
                .collect(Collectors.toMap(MatchDataProvider::name, Function.identity()));
        this.ingestion = ingestion;
        this.innings = innings;
        this.balls = balls;
        this.activeProviderName = activeProviderName;
        log.info("Ingestion providers registered: {} | active = '{}'", providersByName.keySet(), activeProviderName);
    }

    @Scheduled(fixedDelayString = "${app.ingestion.poll-interval-ms}")
    public void tick() {
        MatchDataProvider provider = providersByName.get(activeProviderName);
        if (provider == null) {
            log.warn("No provider registered under name '{}'. Skipping tick.", activeProviderName);
            return;
        }

        for (Long matchId : provider.liveMatchIds()) {
            long[] cursor = lastBallCursor(matchId);
            List<MatchEvent> events = provider.pollSince(matchId, cursor[0], cursor[1]);
            for (MatchEvent ev : events) {
                try {
                    ingestion.ingest(ev);
                } catch (Exception ex) {
                    log.error("Failed to ingest event {} for match {}", ev.type(), matchId, ex);
                }
            }
        }
    }

    private long[] lastBallCursor(long matchId) {
        return innings.findByMatchIdOrderByNumberAsc(matchId).stream()
                .map(i -> balls.findFirstByInningsIdOrderByOverNumDescBallInOverDesc(i.getId()))
                .flatMap(Optional::stream)
                .reduce((a, b) -> b)
                .map(b -> new long[]{b.getOverNum(), b.getBallInOver()})
                .orElse(new long[]{0L, 0L});
    }
}
