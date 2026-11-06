package org.bank.common.exception;

import org.bank.common.error.ErrorCode;

/**
 * Authentication failure. Deliberately generic for credential errors so callers cannot
 * distinguish "unknown user" from "wrong password" (user enumeration).
 */
public class AuthenticationFailedException extends BankException {

    public AuthenticationFailedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static AuthenticationFailedException invalidCredentials() {
        return new AuthenticationFailedException(ErrorCode.INVALID_CREDENTIALS, "Invalid username or password");
    }

    public static AuthenticationFailedException invalidToken() {
        return new AuthenticationFailedException(ErrorCode.INVALID_TOKEN, "Token is invalid, expired or revoked");
    }

    public static AuthenticationFailedException invalidMfaCode() {
        return new AuthenticationFailedException(ErrorCode.MFA_INVALID, "Verification code is invalid or expired");
    }
}
