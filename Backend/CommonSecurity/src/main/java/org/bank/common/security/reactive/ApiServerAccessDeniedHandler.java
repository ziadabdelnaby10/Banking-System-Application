package org.bank.common.security.reactive;

import org.bank.common.error.ErrorCode;
import org.bank.common.security.correlation.CorrelationIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * Answers "403 Forbidden" in the shared JSON error format (WebFlux version of ApiAccessDeniedHandler).
 * 403 = the caller IS authenticated but is not allowed to do this. We log the details on the server
 * but send only a generic message to the client, so attackers learn nothing about our rules.
 */
public class ApiServerAccessDeniedHandler implements ServerAccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiServerAccessDeniedHandler.class);

    private final ReactiveErrorWriter writer;

    public ApiServerAccessDeniedHandler(JsonMapper jsonMapper) {
        this.writer = new ReactiveErrorWriter(jsonMapper);
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        log.warn("[{}] Access denied on {} {}",
                exchange.getResponse().getHeaders().getFirst(CorrelationIds.HEADER),
                exchange.getRequest().getMethod(), exchange.getRequest().getPath().value());
        return writer.write(exchange, ErrorCode.ACCESS_DENIED, "Access is denied");
    }
}
