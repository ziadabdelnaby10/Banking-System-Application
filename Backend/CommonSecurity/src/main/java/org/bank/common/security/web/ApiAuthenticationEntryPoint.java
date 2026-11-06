package org.bank.common.security.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bank.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/** 401 handler: distinguishes "no token" (UNAUTHENTICATED) from "bad token" (INVALID_TOKEN). */
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(ApiAuthenticationEntryPoint.class);

    private final SecurityErrorWriter writer;

    public ApiAuthenticationEntryPoint(JsonMapper jsonMapper) {
        this.writer = new SecurityErrorWriter(jsonMapper);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        boolean badToken = ex instanceof OAuth2AuthenticationException;
        log.warn("Unauthenticated request to {} {} ({})", request.getMethod(), request.getRequestURI(),
                badToken ? "invalid token" : "no credentials");
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        if (badToken) {
            writer.write(request, response, ErrorCode.INVALID_TOKEN, "Token is invalid, expired or revoked");
        } else {
            writer.write(request, response, ErrorCode.UNAUTHENTICATED, "Authentication is required");
        }
    }
}
