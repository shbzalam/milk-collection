package com.zenalyst.milkcollection.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;

/**
 * Translates exceptions into the single {@link ErrorResponse} shape.
 * Stack traces and SQL details are logged, never returned to the caller.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Clock clock;

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        ErrorCode code = ex.errorCode();
        log.info("Rejected {} {}: {} - {}", request.getMethod(), request.getRequestURI(), code, ex.getMessage());
        return respond(code, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException ex,
                                                           HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .sorted(Comparator.comparing(ErrorResponse.FieldError::field))
                .toList();
        log.info("Validation failed for {} {}: {}", request.getMethod(), request.getRequestURI(), fieldErrors);
        ErrorCode code = ErrorCode.VALIDATION_FAILED;
        return ResponseEntity.status(code.status()).body(new ErrorResponse(
                clock.instant(), code.status().value(), code.name(),
                "Request validation failed", request.getRequestURI(), fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                   HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> new ErrorResponse.FieldError(lastPathNode(v), v.getMessage()))
                .sorted(Comparator.comparing(ErrorResponse.FieldError::field))
                .toList();
        ErrorCode code = ErrorCode.VALIDATION_FAILED;
        return ResponseEntity.status(code.status()).body(new ErrorResponse(
                clock.instant(), code.status().value(), code.name(),
                "Request validation failed", request.getRequestURI(), fieldErrors));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception ex, HttpServletRequest request) {
        log.info("Malformed request {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return respond(ErrorCode.MALFORMED_REQUEST, "Request could not be parsed", request);
    }

    /**
     * Spring MVC's own protocol-level exceptions.
     *
     * <p>These must be handled explicitly. The catch-all {@code Exception} handler below would
     * otherwise turn an unknown URL, a wrong HTTP method or an unsupported content type into a
     * 500, which is both wrong and unhelpful to a client.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> handleUnknownPath(Exception ex, HttpServletRequest request) {
        return respond(ErrorCode.RESOURCE_NOT_FOUND,
                "No endpoint " + request.getMethod() + " " + request.getRequestURI(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        ErrorCode code = ErrorCode.METHOD_NOT_ALLOWED;
        BodyBuilder response = ResponseEntity.status(code.status());
        // The Allow header is required for a 405 by the HTTP specification.
        if (ex.getSupportedHttpMethods() != null) {
            response.allow(ex.getSupportedHttpMethods().toArray(HttpMethod[]::new));
        }
        return response.body(ErrorResponse.of(clock.instant(), code,
                ex.getMethod() + " is not supported by " + request.getRequestURI(),
                request.getRequestURI()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "Content type " + ex.getContentType() + " is not supported; use application/json",
                request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponse> handleNotAcceptable(HttpServletRequest request) {
        return respond(ErrorCode.NOT_ACCEPTABLE,
                "This API produces application/json only", request);
    }

    /**
     * A unique or foreign-key constraint fired. These are the last line of defence behind the
     * service-level checks, so reaching here usually means a genuine race, not a coding slip.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                             HttpServletRequest request) {
        log.warn("Database constraint violated on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(ErrorCode.CONSTRAINT_VIOLATION,
                "Operation conflicts with an existing record", request);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> handleLockFailure(Exception ex, HttpServletRequest request) {
        log.warn("Concurrent modification on {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMessage());
        return respond(ErrorCode.CONCURRENT_MODIFICATION,
                "The record was modified concurrently, please retry", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(ErrorCode.INTERNAL_ERROR, "Unexpected server error", request);
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode code, String message, HttpServletRequest request) {
        return ResponseEntity.status(code.status())
                .body(ErrorResponse.of(clock.instant(), code, message, request.getRequestURI()));
    }

    private static String lastPathNode(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        return lastDot < 0 ? path : path.substring(lastDot + 1);
    }
}
