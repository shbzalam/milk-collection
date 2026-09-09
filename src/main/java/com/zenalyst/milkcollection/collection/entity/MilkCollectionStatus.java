package com.zenalyst.milkcollection.collection.entity;

/**
 * Acceptance state of a recorded collection.
 *
 * <p>Only {@code ACCEPTED} is ever written by this MVP: a collection that would breach the
 * holding limit is refused outright and nothing is persisted, so there is no rejected row to
 * represent. The column exists because a persisted refusal or correction trail is the obvious
 * next requirement, and adding one is then a data change rather than a schema redesign.
 */
public enum MilkCollectionStatus {
    ACCEPTED
}
