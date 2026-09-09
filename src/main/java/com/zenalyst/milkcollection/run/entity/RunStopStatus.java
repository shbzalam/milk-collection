package com.zenalyst.milkcollection.run.entity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of one stop within a run.
 *
 * <pre>
 *   PENDING --arrive--> ARRIVED --first collection--> COLLECTING --complete--> COMPLETED
 *      |                   |                                                      ^
 *      |                   +------------------------------------------------------+
 *      |                      (arrived, nobody had milk)
 *      +--skip--> SKIPPED
 * </pre>
 *
 * <p>A stop can only be skipped before the tanker gets there. Once it has arrived the stop is
 * closed by completing it, so a skip can never hide milk that was actually collected.
 */
public enum RunStopStatus {

    PENDING,
    ARRIVED,
    COLLECTING,
    COMPLETED,
    SKIPPED;

    private static final Map<RunStopStatus, Set<RunStopStatus>> ALLOWED_TRANSITIONS = Map.of(
            PENDING, EnumSet.of(ARRIVED, SKIPPED),
            ARRIVED, EnumSet.of(COLLECTING, COMPLETED),
            COLLECTING, EnumSet.of(COMPLETED),
            COMPLETED, EnumSet.noneOf(RunStopStatus.class),
            SKIPPED, EnumSet.noneOf(RunStopStatus.class));

    public boolean canTransitionTo(RunStopStatus target) {
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }

    public Set<RunStopStatus> allowedTransitions() {
        return ALLOWED_TRANSITIONS.get(this);
    }

    /** The tanker is standing at this stop, so milk can be recorded against it. */
    public boolean acceptsCollections() {
        return this == ARRIVED || this == COLLECTING;
    }

    public boolean isTerminal() {
        return ALLOWED_TRANSITIONS.get(this).isEmpty();
    }
}
