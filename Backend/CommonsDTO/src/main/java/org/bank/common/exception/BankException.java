package org.bank.common.exception;

import org.bank.common.error.ErrorCode;

/**
 * Base class for all expected (business/security) failures. The {@link ErrorCode} drives the
 * HTTP status and the client-facing code; the message must be safe to expose to callers.
 */
public class BankException extends RuntimeException {

    private final ErrorCode errorCode;

    public BankException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
