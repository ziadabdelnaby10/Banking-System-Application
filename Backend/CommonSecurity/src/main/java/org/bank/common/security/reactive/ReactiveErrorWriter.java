package org.bank.common.security.reactive;

import org.bank.common.error.ErrorCode;
import org.bank.common.security.correlation.CorrelationIds;
import org.bank.common.security.web.SecurityErrors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * Small helper shared by the 401 and 403 handlers: it builds the standard JSON error body and writes it
 * to the HTTP response (status, content type, body). Package-private: only used inside this package.
 */
class ReactiveErrorWriter {

    private final JsonMapper jsonMapper;

    ReactiveErrorWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    Mono<Void> write(ServerWebExchange exchange, ErrorCode code, String message) {
        var response = exchange.getResponse();
        // Correlation ids travel with the exchange (the WebFilter set the header), not with a thread-local MDC.
        String correlationId = response.getHeaders().getFirst(CorrelationIds.HEADER);
        byte[] payload = jsonMapper.writeValueAsBytes(
                SecurityErrors.body(code, message, exchange.getRequest().getPath().value(), correlationId));

        response.setStatusCode(HttpStatus.valueOf(code.httpStatus()));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(payload)));
    }
}
