package org.bank.identity.security;

import org.bank.identity.model.User;

/** Port for issuing access tokens, so the token mechanism can be swapped without touching the auth flow. */
public interface AccessTokenIssuer {

    IssuedAccessToken issue(User user, UserAuthorities authorities, long sessionId, boolean mfaVerified);

    record IssuedAccessToken(String value, long expiresInSeconds) {
    }
}
