package com.coachsim.ingestion.cricapi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Thin HTTP client over the cricapi.com v2 endpoints we use.
 * Returns the typed {@code data} payload or empty when the call fails — the
 * free tier rate-limits at 100 requests/day so we deliberately fail soft and
 * never throw out of the ingestion loop.
 */
public class CricApiClient {

    private static final Logger log = LoggerFactory.getLogger(CricApiClient.class);

    private final RestClient http;
    private final String apiKey;
    private final ObjectMapper mapper;

    public CricApiClient(String baseUrl, String apiKey, ObjectMapper mapper) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.mapper = mapper;
    }

    /** {@code /v1/currentMatches?apikey=…&offset=0} */
    public List<CricApiDto.MatchListItem> currentMatches() {
        try {
            String body = http.get()
                    .uri(uri -> uri.path("/currentMatches")
                            .queryParam("apikey", apiKey)
                            .queryParam("offset", 0)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        log.warn("CricAPI currentMatches HTTP {}", resp.getStatusCode().value());
                    })
                    .body(String.class);
            CricApiDto.Envelope<List<CricApiDto.MatchListItem>> env =
                    mapper.readValue(body, new TypeReference<>() {});
            if (!"success".equalsIgnoreCase(env.status())) {
                log.warn("CricAPI currentMatches returned status='{}' info='{}'", env.status(), env.info());
                return List.of();
            }
            return env.data() == null ? List.of() : env.data();
        } catch (Exception ex) {
            log.warn("CricAPI currentMatches failed: {}", ex.getMessage());
            return List.of();
        }
    }

    /** {@code /v1/match_scorecard?apikey=…&id=…} */
    public CricApiDto.MatchScorecard scorecard(String matchId) {
        try {
            String body = http.get()
                    .uri(uri -> uri.path("/match_scorecard")
                            .queryParam("apikey", apiKey)
                            .queryParam("id", matchId)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        log.warn("CricAPI scorecard HTTP {}", resp.getStatusCode().value());
                    })
                    .body(String.class);
            CricApiDto.Envelope<CricApiDto.MatchScorecard> env =
                    mapper.readValue(body, new TypeReference<>() {});
            if (!"success".equalsIgnoreCase(env.status())) {
                log.warn("CricAPI scorecard returned status='{}' info='{}'", env.status(), env.info());
                return null;
            }
            return env.data();
        } catch (Exception ex) {
            log.warn("CricAPI scorecard({}) failed: {}", matchId, ex.getMessage());
            return null;
        }
    }
}
