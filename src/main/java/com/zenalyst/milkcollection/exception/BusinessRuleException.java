package com.zenalyst.milkcollection.exception;

/**
 * A domain rule rejected the operation. The request was well-formed; the state of the
 * system does not permit it.
 */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
