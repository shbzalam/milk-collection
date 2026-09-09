package com.zenalyst.milkcollection.run.entity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of a collection run.
 *
 * <pre>
 *   PLANNED --start--> STARTED --first arrival--> IN_PROGRESS --complete--> COMPLETED
 *      |                  |                                                    ^
 *      |                  +----------------------------------------------------+
 *      |                     (a run whose every stop was skipped)
 *      +--cancel--> CANCELLED
 * </pre>
 *
 * <p>Cancellation is only possible before the tanker leaves: once milk is on board the run has
 * to be closed out, not made to disappear. COMPLETED and CANCELLED are terminal, so the
 * transitions the brief calls out as invalid - COMPLETED to STARTED, CANCELLED to IN_PROGRESS -
 * are simply absent from this table.
 */
public enum RunStatus {

    PLANNED,
    STARTED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    private static final Map<RunStatus, Set<RunStatus>> ALLOWED_TRANSITIONS = Map.of(
            PLANNED, EnumSet.of(STARTED, CANCELLED),
            STARTED, EnumSet.of(IN_PROGRESS, COMPLETED),
            IN_PROGRESS, EnumSet.of(COMPLETED),
            COMPLETED, EnumSet.noneOf(RunStatus.class),
            CANCELLED, EnumSet.noneOf(RunStatus.class));

    public boolean canTransitionTo(RunStatus target) {
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }

    public Set<RunStatus> allowedTransitions() {
        return ALLOWED_TRANSITIONS.get(this);
    }

    /** The tanker is out on the road: milk may be collected against this run. */
    public boolean isOnTheRoad() {
        return this == STARTED || this == IN_PROGRESS;
    }

    public boolean isTerminal() {
        return ALLOWED_TRANSITIONS.get(this).isEmpty();
    }
}
