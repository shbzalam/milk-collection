package com.zenalyst.milkcollection.tanker.entity;

/**
 * A tanker is only eligible for a run when ACTIVE. IN_MAINTENANCE is kept distinct from
 * INACTIVE because it is a temporary, operational state the planner needs to see.
 */
public enum TankerStatus {
    ACTIVE,
    IN_MAINTENANCE,
    INACTIVE
}
