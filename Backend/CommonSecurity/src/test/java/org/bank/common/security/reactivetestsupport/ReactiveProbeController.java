package org.bank.common.security.reactivetestsupport;

import org.bank.common.security.reactive.ReactiveCurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * TEST HELPER: tiny endpoints used by the security tests:
 *  /probe/open        - made public in the test, needs no token;
 *  /probe/me          - protected; returns "username:branch" of the logged-in user;
 *  /probe/correlation - returns the correlation id the handler received.
 */
@RestController
public class ReactiveProbeController {

    @GetMapping("/probe/open")
    Mono<String> open() {
        return Mono.just("open");
    }

    @GetMapping("/probe/me")
    Mono<String> me() {
        return ReactiveCurrentUser.require().map(user -> user.username() + ":" + user.branchCode());
    }

    /** Lets a test see which correlation id reached the "downstream" handler. */
    @GetMapping("/probe/correlation")
    Mono<String> correlation(@RequestHeader("X-Correlation-Id") String correlationId) {
        return Mono.just(correlationId);
    }
}
