package org.bank.common.security.reactive;

import org.bank.common.error.ErrorCode;
import org.bank.common.security.correlation.CorrelationIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * Answers "401 Unauthorized" in the shared JSON error format (WebFlux version of ApiAuthenticationEntryPoint).
 * 401 = we don't know who you are. Two cases are told apart so the frontend can react correctly:
 *  - UNAUTHENTICATED: no token was sent (send the user to the login page);
 *  - INVALID_TOKEN: a token was sent but is expired/forged/wrong (try refreshing, else log in again).
 */
public class ApiServerAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(ApiServerAuthenticationEntryPoint.class);

    private final ReactiveErrorWriter writer;

    public ApiServerAuthenticationEntryPoint(JsonMapper jsonMapper) {
        this.writer = new ReactiveErrorWriter(jsonMapper);
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        // Spring throws OAuth2AuthenticationException when a token WAS provided but failed verification.
        boolean badToken = ex instanceof OAuth2AuthenticationException;
        log.warn("[{}] Unauthenticated request to {} {} ({})",
                exchange.getResponse().getHeaders().getFirst(CorrelationIds.HEADER),
                exchange.getRequest().getMethod(), exchange.getRequest().getPath().value(),
                badToken ? "invalid token" : "no credentials");
        // Standard HTTP hint telling clients which auth scheme is expected.
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return badToken
                ? writer.write(exchange, ErrorCode.INVALID_TOKEN, "Token is invalid, expired or revoked")
                : writer.write(exchange, ErrorCode.UNAUTHENTICATED, "Authentication is required");
    }
}
