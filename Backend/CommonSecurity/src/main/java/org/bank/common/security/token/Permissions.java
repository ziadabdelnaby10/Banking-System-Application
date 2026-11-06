package org.bank.common.security.token;

/** Functional permission codes shared across services (JWT "permissions" claim, plain authorities). */
public final class Permissions {

    public static final String USER_CREATE = "USER_CREATE";
    public static final String USER_VIEW = "USER_VIEW";
    public static final String USER_UPDATE = "USER_UPDATE";
    public static final String USER_LOCK = "USER_LOCK";
    public static final String ROLE_ASSIGN = "ROLE_ASSIGN";
    public static final String AUTH_LOGIN = "AUTH_LOGIN";
    public static final String AUTH_MFA_VERIFY = "AUTH_MFA_VERIFY";
    public static final String AUDIT_VIEW = "AUDIT_VIEW";

    private Permissions() {
    }
}
