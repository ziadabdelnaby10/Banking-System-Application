package org.bank.customer.aop;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.bank.common.error.ApiErrorDetail;
import org.bank.common.error.ErrorCode;
import org.bank.common.error.FieldViolation;
import org.bank.common.exception.BankException;
import org.bank.common.security.correlation.CorrelationIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Translates every failure into the shared {@link ApiErrorDetail} contract (same shape as the Identity service).
 * Internal details are logged, never returned.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BankException.class)
    public ResponseEntity<ApiErrorDetail> handleBank(BankException ex, HttpServletRequest request) {
        return respond(ex.getErrorCode(), ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied on {} {}", request.getMethod(), request.getRequestURI());
        return respond(ErrorCode.ACCESS_DENIED, "You do not have permission to perform this action", request.getRequestURI());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return respond(ErrorCode.UNAUTHENTICATED, "Authentication is required", request.getRequestURI());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorDetail> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.httpStatus())
                .body(error(ErrorCode.VALIDATION_FAILED, "Validation failed", request.getRequestURI())
                        .withViolations(violations));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorDetail> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        // A unique-constraint race the pre-checks could not see (same national id, second open KYC submission, ...).
        log.warn("Data integrity violation on {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return respond(ErrorCode.DUPLICATE_RESOURCE, "The request conflicts with existing data", request.getRequestURI());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorDetail> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Concurrent modification on {} {}", request.getMethod(), request.getRequestURI());
        return respond(ErrorCode.DUPLICATE_RESOURCE, "The record was changed by someone else; reload it and try again",
                request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request.getRequestURI());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldViolation(f.getField(), f.getDefaultMessage()))
                .toList();
        ApiErrorDetail body = error(ErrorCode.VALIDATION_FAILED, "Validation failed", pathOf(request))
                .withViolations(violations);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.httpStatus()).headers(headers).body(body);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code = statusCode.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.BAD_REQUEST;
        String message = statusCode.is5xxServerError() ? "An unexpected error occurred" : reasonOf(statusCode);
        ApiErrorDetail payload = ApiErrorDetail.of(statusCode.value(), reasonOf(statusCode), code, message,
                pathOf(request), CorrelationIds.current());
        return ResponseEntity.status(statusCode).headers(headers).body(payload);
    }

    private static ResponseEntity<ApiErrorDetail> respond(ErrorCode code, String message, String path) {
        return ResponseEntity.status(code.httpStatus()).body(error(code, message, path));
    }

    private static ApiErrorDetail error(ErrorCode code, String message, String path) {
        int status = code.httpStatus();
        return ApiErrorDetail.of(status, reasonOf(HttpStatusCode.valueOf(status)), code, message, path, CorrelationIds.current());
    }

    private static String reasonOf(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : "Error";
    }

    private static String pathOf(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest ? servletRequest.getRequest().getRequestURI() : "";
    }
}
