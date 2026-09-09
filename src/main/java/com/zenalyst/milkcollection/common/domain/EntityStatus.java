package com.zenalyst.milkcollection.common.domain;

/**
 * Lifecycle flag shared by master-data entities. Master data is deactivated, never deleted,
 * because historical runs and collections must keep resolving their references.
 */
public enum EntityStatus {
    ACTIVE,
    INACTIVE
}
