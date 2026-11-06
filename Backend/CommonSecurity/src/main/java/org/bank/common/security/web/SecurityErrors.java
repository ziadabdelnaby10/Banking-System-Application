package org.bank.common.security.web;

import org.bank.common.error.ApiErrorDetail;
import org.bank.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Single place that creates the JSON error object returned on 401/403 (status, error code, message,
 * request path, correlation id). Both the servlet and the WebFlux error writers call it, so Identity and
 * the Gateway return identical error bodies and the frontend only has to handle one format.
 * Builds the shared error payload for security failures. Kept free of servlet/reactive types so the
 * servlet and reactive stacks answer with exactly the same body.
 */
public final class SecurityErrors {

    private SecurityErrors() {
    }

    public static ApiErrorDetail body(ErrorCode code, String message, String path, String correlationId) {
        HttpStatus status = HttpStatus.valueOf(code.httpStatus());
        return ApiErrorDetail.of(status.value(), status.getReasonPhrase(), code, message, path, correlationId);
    }
}
