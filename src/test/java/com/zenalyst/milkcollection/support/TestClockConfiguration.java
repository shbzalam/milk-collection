package com.zenalyst.milkcollection.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Replaces the application clock with one the test controls. Imported only by the tests that
 * need to move time; a different bean name plus {@code @Primary} avoids relying on
 * bean-definition overriding.
 */
@TestConfiguration
public class TestClockConfiguration {

    /** 2026-09-09 05:00 in Asia/Kolkata - the start of a morning shift. */
    public static final Instant SHIFT_START = Instant.parse("2026-09-08T23:30:00Z");

    @Bean
    @Primary
    public Clock mutableTestClock() {
        return new MutableTestClock(SHIFT_START, ZoneId.of("Asia/Kolkata"));
    }
}
