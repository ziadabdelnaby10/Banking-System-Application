package org.bank.common.security.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bank.common.util.UuidV7Generator;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Accepts a well-formed X-Correlation-Id or generates one, exposes it through MDC and echoes it back.
 * Untrusted values are never reflected: anything outside {@code [A-Za-z0-9._-]{1,64}} is replaced
 * (prevents log/header injection).
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(CorrelationIds.HEADER);
        String correlationId = CorrelationIds.isValid(incoming) ? incoming : UuidV7Generator.generateUUIDV7().toString();

        MDC.put(CorrelationIds.MDC_KEY, correlationId);
        response.setHeader(CorrelationIds.HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationIds.MDC_KEY);
        }
    }
}
