package org.bank.common.security.reactive;

import org.bank.common.error.ErrorCode;
import org.bank.common.exception.AuthenticationFailedException;
import org.bank.common.security.principal.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;

/**
 * HOW TO ASK "WHO IS CALLING?" in the Gateway (WebFlux). Returns the logged-in user (username, branch,
 * roles...) taken from the verified token. Results come back wrapped in a Mono (a "value that will
 * arrive later"), because WebFlux code never blocks a thread waiting.
 *  - get(): empty if nobody is logged in;
 *  - require(): fails with an "unauthenticated" error if nobody is logged in.
 * Reactive access to the caller. Use this instead of {@code @AuthenticationPrincipal AuthenticatedUser}
 * in WebFlux handlers: {@code AuthenticatedUser} is a {@link java.security.Principal}, so WebFlux resolves
 * such a parameter to the whole Authentication before Spring Security's resolver is consulted.
 */
public final class ReactiveCurrentUser {

    private ReactiveCurrentUser() {
    }

    public static Mono<AuthenticatedUser> get() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .ofType(AuthenticatedUser.class);
    }

    public static Mono<AuthenticatedUser> require() {
        return get().switchIfEmpty(Mono.error(
                new AuthenticationFailedException(ErrorCode.UNAUTHENTICATED, "Authentication is required")));
    }
}
