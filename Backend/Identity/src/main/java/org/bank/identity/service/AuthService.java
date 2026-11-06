package org.bank.identity.service;

import org.bank.common.dto.request.LoginRequest;
import org.bank.common.dto.request.MfaVerifyRequest;
import org.bank.common.dto.request.RefreshTokenRequest;
import org.bank.common.dto.response.LoginResponse;
import org.bank.common.dto.response.TokenResponse;

public interface AuthService {

    /** Verifies credentials; returns tokens, or an MFA challenge when the user has a second factor. */
    LoginResponse login(LoginRequest request, ClientContext context);

    /** Completes a login that returned an MFA challenge. */
    TokenResponse verifyMfa(MfaVerifyRequest request, ClientContext context);

    /** Rotates the refresh token and issues a new access token. */
    TokenResponse refresh(RefreshTokenRequest request, ClientContext context);

    /** Revokes the session behind the refresh token. Idempotent; never reveals token validity. */
    void logout(RefreshTokenRequest request);
}
