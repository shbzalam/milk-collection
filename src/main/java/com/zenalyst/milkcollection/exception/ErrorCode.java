package com.zenalyst.milkcollection.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes. These are part of the public API contract:
 * clients branch on {@code code}, never on the human-readable message.
 */
public enum ErrorCode {

    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE),

    DUPLICATE_RESOURCE(HttpStatus.CONFLICT),
    CONSTRAINT_VIOLATION(HttpStatus.CONFLICT),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT),

    // Planning
    INVALID_ROUTE(HttpStatus.CONFLICT),
    ROUTE_VERSION_NOT_PUBLISHED(HttpStatus.CONFLICT),
    ROUTE_VERSION_IMMUTABLE(HttpStatus.CONFLICT),
    NO_TANKERS_AVAILABLE(HttpStatus.CONFLICT),

    // Operations
    INVALID_RUN_STATE(HttpStatus.CONFLICT),
    INVALID_STOP_STATE(HttpStatus.CONFLICT),
    TANKER_ALREADY_ASSIGNED(HttpStatus.CONFLICT),
    TANKER_CAPACITY_EXCEEDED(HttpStatus.CONFLICT),
    MILK_HOLDING_TIME_EXCEEDED(HttpStatus.CONFLICT),
    FARMER_NOT_ASSIGNED_TO_STOP(HttpStatus.CONFLICT),
    DUPLICATE_COLLECTION(HttpStatus.CONFLICT),
    INACTIVE_RESOURCE(HttpStatus.CONFLICT),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
