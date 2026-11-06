package org.bank.common.security.reactive;

import org.bank.common.security.correlation.CorrelationIds;
import org.bank.common.util.UuidV7Generator;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WHAT IT DOES: gives every request a "tracking number" (the X-Correlation-Id header).
 * With many services, one user action touches several of them; if each logs the same id you can follow
 * the whole journey in the logs.
 *  - If the client sent a valid id, we keep it; otherwise we generate a new one (never trusting junk).
 *  - The id is put on the response so the client can quote it when reporting a problem.
 *  - The id is put on the request that goes on to the next service (the Gateway forwards it).
 * Reactive counterpart of the servlet CorrelationIdFilter.
 * <p>
 * A reactive request hops between threads, so the id is carried in the Reactor context and exchange
 * headers instead of a thread-local MDC.
 */
public class CorrelationIdWebFilter implements WebFilter, Ordered {

    /** Runs before the Spring Security web filter chain so 401/403 responses carry the id too. */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 1) Reuse the client's id if it looks safe, otherwise create a fresh one.
        String incoming = exchange.getRequest().getHeaders().getFirst(CorrelationIds.HEADER);
        String correlationId = CorrelationIds.isValid(incoming) ? incoming : UuidV7Generator.generateUUIDV7().toString();

        // 2) Put it on the response.
        exchange.getResponse().getHeaders().set(CorrelationIds.HEADER, correlationId);
        // A gateway copies the downstream response headers, which already carry the same id: overwrite
        // right before commit so the client never sees it twice.
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(CorrelationIds.HEADER, correlationId);
            return Mono.empty();
        });
        // 3) Put it on the request too (requests are read-only, so we build a modified copy) so the
        //    next handler / downstream service receives it.
        ServerWebExchange forwarded = exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(CorrelationIds.HEADER, correlationId)))
                .build();
        // 4) Continue the chain; also store the id in the Reactor context so reactive code can read it.
        return chain.filter(forwarded)
                .contextWrite(context -> context.put(CorrelationIds.MDC_KEY, correlationId));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
