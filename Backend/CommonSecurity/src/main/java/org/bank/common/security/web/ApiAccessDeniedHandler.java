package org.bank.common.security.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bank.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/** 403 handler. Denials are logged as security events (who, what) without leaking details to the caller. */
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiAccessDeniedHandler.class);

    private final SecurityErrorWriter writer;

    public ApiAccessDeniedHandler(JsonMapper jsonMapper) {
        this.writer = new SecurityErrorWriter(jsonMapper);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        String caller = SecurityContextHolder.getContext().getAuthentication() == null
                ? "anonymous" : SecurityContextHolder.getContext().getAuthentication().getName();
        log.warn("Access denied for {} on {} {}", caller, request.getMethod(), request.getRequestURI());
        writer.write(request, response, ErrorCode.ACCESS_DENIED, "Access is denied");
    }
}
