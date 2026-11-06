package org.bank.common.dto.response;

import org.bank.common.dto.enums.MfaMethod;

import java.time.OffsetDateTime;

/**
 * @param challengeToken opaque token to submit together with the verification code
 * @param method         channel the code was delivered through
 * @param expiresAt      after this instant the challenge is dead and the user must log in again
 */
public record MfaChallengeResponse(String challengeToken, MfaMethod method, OffsetDateTime expiresAt) {

    @Override
    public String toString() {
        return "MfaChallengeResponse[method=" + method + ", expiresAt=" + expiresAt + "]";
    }
}
