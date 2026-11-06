package org.bank.common.security.principal;

import org.bank.common.exception.AuthenticationFailedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Static access to the caller for code that is not a controller (services, auditing). */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AuthenticatedUser> get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static AuthenticatedUser require() {
        return get().orElseThrow(() -> new AuthenticationFailedException(
                org.bank.common.error.ErrorCode.UNAUTHENTICATED, "Authentication is required"));
    }
}
