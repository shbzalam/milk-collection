package com.zenalyst.milkcollection.route.planning;

import com.zenalyst.milkcollection.exception.ErrorCode;

import java.util.List;

/**
 * Outcome of checking a projected schedule against a tanker.
 *
 * <p>Every violation carries the same {@link ErrorCode} the API would return, so a report can
 * be shown to a planner or converted into a rejection without re-deriving the reason.
 */
public record FeasibilityReport(boolean feasible, List<Violation> violations,
                                ProjectedSchedule schedule) {

    public record Violation(ErrorCode code, String message) {
    }
}
