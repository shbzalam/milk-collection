package com.zenalyst.milkcollection.route.entity;

/**
 * Lifecycle of a route version.
 *
 * <pre>
 *   DRAFT ---publish---> PUBLISHED ---superseded by a newer publish---> ARCHIVED
 * </pre>
 *
 * Stops may only be changed while DRAFT. PUBLISHED and ARCHIVED versions are immutable, which
 * is what lets a historical run reproduce exactly the plan it drove.
 */
public enum RouteVersionStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED;

    public boolean isEditable() {
        return this == DRAFT;
    }

    /** Runs may only be created against the current plan of record. */
    public boolean isRunnable() {
        return this == PUBLISHED;
    }
}
