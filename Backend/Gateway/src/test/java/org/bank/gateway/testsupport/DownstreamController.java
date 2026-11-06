package org.bank.gateway.testsupport;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import org.springframework.web.server.ServerWebExchange;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TEST HELPER: pretends to be a real backend service (like Identity) inside the test, so we can check
 * what the Gateway forwards to it. It has two jobs:
 *  - echo back every header it received, so tests can assert on them;
 *  - count how many times it was called (HITS), so tests can prove that a rejected request
 *    never reached the service at all.
 * Stand-in for a downstream service: reports the headers it received so tests can verify what the gateway
 * forwarded, and counts calls so tests can prove a rejected request never reached it.
 */
@RestController
public class DownstreamController {

    public static final AtomicInteger HITS = new AtomicInteger();

    @GetMapping("/downstream/{name}")
    Mono<Map<String, String>> echo(ServerWebExchange exchange) {
        HITS.incrementAndGet();
        HttpHeaders headers = exchange.getRequest().getHeaders();
        Map<String, String> received = new LinkedHashMap<>();
        headers.headerNames().forEach(name -> received.put(name.toLowerCase(Locale.ROOT), headers.getFirst(name)));
        return Mono.just(received);
    }
}
