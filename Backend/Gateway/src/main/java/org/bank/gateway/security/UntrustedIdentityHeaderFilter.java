package org.bank.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;

/**
 * WHAT IT DOES: deletes any header starting with "X-User-" that a client sends.
 * Why: a malicious client could send "X-User-Roles: ADMIN" and hope a service believes it. In this
 * system the ONLY proof of identity is the signed token, which each service re-verifies itself. Removing
 * these headers at the front door means a fake one can never reach a service, even if one is later
 * (wrongly) coded to read it.
 * <p>
 * A GlobalFilter runs for every route the Gateway forwards. Removes client-supplied {@code X-User-*}
 * headers before routing. The platform identifies callers only by
 * the signed JWT, which every service verifies itself; no service may trust an identity header, and
 * dropping them here means a spoofed one can never reach a service even if one wrongly read it.
 */
@Component
public class UntrustedIdentityHeaderFilter implements GlobalFilter, Ordered {

    static final String UNTRUSTED_PREFIX = "x-user-";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Find every incoming header whose name starts with "x-user-" (case-insensitive).
        List<String> spoofed = exchange.getRequest().getHeaders().headerNames().stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(UNTRUSTED_PREFIX))
                .toList();
        // Nothing suspicious: pass the request on untouched.
        if (spoofed.isEmpty()) {
            return chain.filter(exchange);
        }
        // Requests are read-only, so build a cleaned copy without those headers and continue with it.
        ServerHttpRequest sanitized = exchange.getRequest().mutate()
                .headers(headers -> spoofed.forEach(headers::remove))
                .build();
        return chain.filter(exchange.mutate().request(sanitized).build());
    }

    // Run first (lowest number = earliest) so nothing else ever sees the fake headers.
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
