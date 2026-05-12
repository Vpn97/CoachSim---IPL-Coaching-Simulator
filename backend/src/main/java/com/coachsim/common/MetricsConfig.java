package com.coachsim.common;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Custom Prometheus metrics. Exposed at /actuator/prometheus.
 *
 * Pre-creates counters used by the decision/scoring pipeline so they appear in
 * /actuator/prometheus even before the first event arrives — which makes
 * dashboards friendlier.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public Counter fanDecisionsSubmitted(MeterRegistry registry,
                                         @Value("${app.ingestion.provider}") String provider) {
        return Counter.builder("coachsim.fan_decisions.submitted")
                .description("Number of fan decisions accepted by the API")
                .tag("provider", provider)
                .register(registry);
    }

    @Bean
    public Counter fanDecisionsScored(MeterRegistry registry,
                                      @Value("${app.ingestion.provider}") String provider) {
        return Counter.builder("coachsim.fan_decisions.scored")
                .description("Number of fan decisions scored by the tactical merit engine")
                .tag("provider", provider)
                .register(registry);
    }

    @Bean
    public Counter ballsIngested(MeterRegistry registry,
                                 @Value("${app.ingestion.provider}") String provider) {
        return Counter.builder("coachsim.balls.ingested")
                .description("Number of balls ingested into the system")
                .tag("provider", provider)
                .register(registry);
    }
}
