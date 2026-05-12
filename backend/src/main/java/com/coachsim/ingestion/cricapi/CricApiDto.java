package com.coachsim.ingestion.cricapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Minimal DTOs for the cricapi.com v2 API.
 * We intentionally cover only the fields we read — everything else is ignored
 * via {@code @JsonIgnoreProperties(ignoreUnknown = true)} so future vendor
 * schema additions don't break us.
 *
 * Docs: https://cricapi.com/how-to-use/
 */
public final class CricApiDto {

    private CricApiDto() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Envelope<T>(String apikey, String status, T data, String info) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MatchListItem(
            String id,
            String name,
            String matchType,
            String status,
            String venue,
            String date,
            String dateTimeGMT,
            List<String> teams,
            Boolean matchStarted,
            Boolean matchEnded,
            List<ScoreLine> score
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScoreLine(
            Integer r,        // runs
            Integer w,        // wickets
            Double o,         // overs
            String inning     // e.g. "Mumbai Indians Inning 1"
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MatchScorecard(
            String id,
            String name,
            String status,
            List<String> teams,
            List<ScoreLine> score,
            List<InningCard> scorecard
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InningCard(
            String title,            // "Mumbai Indians Inning 1"
            List<BattingLine> batting,
            List<BowlingLine> bowling
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BattingLine(
            String batsman,
            Integer r, Integer b, Integer fours, Integer sixes,
            String sr, String dismissal
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BowlingLine(
            String bowler,
            Double o, Integer m, Integer r, Integer w
    ) {}
}
