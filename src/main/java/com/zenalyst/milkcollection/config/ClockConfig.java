package com.zenalyst.milkcollection.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Time is injected rather than read from {@code Instant.now()} so that business logic which
 * depends on "now" (ETA, holding-time checks, run timestamps) is deterministically testable.
 *
 * <p>Only the audit columns {@code createdAt}/{@code updatedAt} use Hibernate's own timestamp
 * generation, because they never participate in business decisions.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(AppProperties appProperties) {
        return Clock.system(appProperties.timeZone());
    }
}
