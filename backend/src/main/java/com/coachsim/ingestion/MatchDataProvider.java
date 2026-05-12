package com.coachsim.ingestion;

import java.util.List;

/**
 * Pluggable source of live (or simulated) match data. Implementations:
 *  - {@link MockMatchDataProvider}  : admin-controlled, advances on demand
 *  - {@link ExternalCricketApiProvider} : polls a third-party HTTP API
 *
 * Selection is driven by the {@code app.ingestion.provider} property.
 */
public interface MatchDataProvider {

    /** Identifier matched against {@code app.ingestion.provider}. */
    String name();

    /** Match IDs the provider considers currently live and worth polling. */
    List<Long> liveMatchIds();

    /** Pull new events for the given match since the previously-seen position. */
    List<MatchEvent> pollSince(long matchId, long lastBallOver, long lastBallInOver);
}
