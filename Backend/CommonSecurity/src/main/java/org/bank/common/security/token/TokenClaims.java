package org.bank.common.security.token;

/**
 * Names of the custom JWT claims. This is the contract between the token issuer (Identity)
 * and every validating service, so it lives in the shared library.
 */
public final class TokenClaims {

    public static final String USERNAME = "preferred_username";
    public static final String ROLES = "roles";
    public static final String PERMISSIONS = "permissions";
    public static final String BRANCH = "branch";
    /** true when the user completed a second factor for this session. */
    public static final String MFA = "mfa";
    /** Identity session id; lets a service correlate a token with a revocable session. */
    public static final String SESSION_ID = "sid";

    private TokenClaims() {
    }
}
