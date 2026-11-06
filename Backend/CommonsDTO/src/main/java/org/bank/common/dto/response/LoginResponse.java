package org.bank.common.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Either {@code tokens} (login complete) or {@code mfaChallenge} (a second factor is required) is present.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(Status status, TokenResponse tokens, MfaChallengeResponse mfaChallenge) {

    public enum Status {
        AUTHENTICATED,
        MFA_REQUIRED
    }

    public static LoginResponse authenticated(TokenResponse tokens) {
        return new LoginResponse(Status.AUTHENTICATED, tokens, null);
    }

    public static LoginResponse mfaRequired(MfaChallengeResponse challenge) {
        return new LoginResponse(Status.MFA_REQUIRED, null, challenge);
    }
}
