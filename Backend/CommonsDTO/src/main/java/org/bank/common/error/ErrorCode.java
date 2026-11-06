package org.bank.common.error;

/**
 * Stable, machine-readable error codes shared by every service.
 * Clients branch on {@link #name()}; the HTTP status is a framework-free int so this module
 * does not need to know about Spring's HttpStatus.
 */
public enum ErrorCode {
    BAD_REQUEST(400),
    VALIDATION_FAILED(422),
    BUSINESS_RULE_VIOLATION(422),
    UNAUTHENTICATED(401),
    INVALID_CREDENTIALS(401),
    INVALID_TOKEN(401),
    MFA_INVALID(401),
    ACCESS_DENIED(403),
    ACCOUNT_DISABLED(403),
    RESOURCE_NOT_FOUND(404),
    DUPLICATE_RESOURCE(409),
    ACCOUNT_LOCKED(423),
    INTERNAL_ERROR(500);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
