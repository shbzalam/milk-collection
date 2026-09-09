package com.zenalyst.milkcollection.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A clock the test drives by hand.
 *
 * <p>This is the payoff of injecting {@link Clock} instead of calling {@code Instant.now()}:
 * a run that has fallen hours behind schedule can be reproduced in milliseconds, which is the
 * only practical way to test the holding-time rule.
 */
public class MutableTestClock extends Clock {

    private final ZoneId zone;
    private volatile Instant instant;

    public MutableTestClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId otherZone) {
        return new MutableTestClock(instant, otherZone);
    }

    @Override
    public Instant instant() {
        return instant;
    }

    public void advanceBy(Duration duration) {
        this.instant = this.instant.plus(duration);
    }

    public void setTo(Instant newInstant) {
        this.instant = newInstant;
    }
}
