package org.bank.common.security.principal;

import java.io.Serializable;
import java.security.Principal;
import java.util.Set;
import java.util.UUID;

/**
 * The authenticated caller as seen by any service. Obtain it with
 * {@code @AuthenticationPrincipal AuthenticatedUser user} or {@code CurrentUser.require()}.
 */
public record AuthenticatedUser(
        UUID userId,
        String username,
        String branchCode,
        Set<String> roles,
        Set<String> permissions,
        boolean mfaVerified,
        String sessionId
) implements Principal, Serializable {

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    /** The principal name is the immutable user id, which is what audit records should store. */
    @Override
    public String getName() {
        return userId.toString();
    }
}
