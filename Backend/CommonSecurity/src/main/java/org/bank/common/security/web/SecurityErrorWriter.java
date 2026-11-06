package org.bank.common.security.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bank.common.error.ApiErrorDetail;
import org.bank.common.error.ErrorCode;
import org.bank.common.security.correlation.CorrelationIds;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Writes {@link ApiErrorDetail} JSON from the security filter chain (outside MVC's exception handling). */
class SecurityErrorWriter {

    private final JsonMapper jsonMapper;

    SecurityErrorWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String message)
            throws IOException {
        ApiErrorDetail body = SecurityErrors.body(code, message, request.getRequestURI(), CorrelationIds.current());
        response.setStatus(body.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getOutputStream().write(jsonMapper.writeValueAsBytes(body));
    }
}
