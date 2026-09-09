package com.zenalyst.milkcollection.tracking.dto;

/** What a farmer actually wants to know when they ring up. */
public enum FarmerCollectionState {

    /** No run covering this collection point is planned or under way today. */
    NO_RUN_SCHEDULED,
    /** A run exists but the tanker has not left the plant yet. */
    SCHEDULED,
    /** The tanker is on the road and has not reached this collection point. */
    EN_ROUTE,
    /** The tanker is standing at this collection point right now. */
    AT_YOUR_COLLECTION_POINT,
    /** Collection here is finished. */
    COLLECTED,
    /** The tanker will not be stopping here on this run. */
    SKIPPED
}
