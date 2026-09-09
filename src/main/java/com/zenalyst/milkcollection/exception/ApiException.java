package com.zenalyst.milkcollection.exception;

/**
 * Base class for every exception that maps to a deliberate API response.
 * The {@link ErrorCode} carries both the client-facing code and the HTTP status.
 */
public abstract class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    protected ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
