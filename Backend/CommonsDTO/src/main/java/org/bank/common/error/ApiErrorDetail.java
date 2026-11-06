package org.bank.common.error;

import java.time.Instant;
import java.util.List;

/**
 * Canonical error payload returned by every service.
 *
 * @param timestamp     when the error was produced (UTC)
 * @param status        HTTP status code
 * @param error         HTTP reason phrase
 * @param code          stable machine-readable code
 * @param message       safe, human-readable message (never internal details)
 * @param path          request URI
 * @param correlationId value of X-Correlation-Id for log/event tracing
 * @param violations    field-level validation failures, empty when not applicable
 */
public record ApiErrorDetail(
        Instant timestamp,
        int status,
        String error,
        ErrorCode code,
        String message,
        String path,
        String correlationId,
        List<FieldViolation> violations
) {

    public static ApiErrorDetail of(int status, String reason, ErrorCode code, String message,
                                    String path, String correlationId) {
        return new ApiErrorDetail(Instant.now(), status, reason, code, message, path, correlationId, List.of());
    }

    public ApiErrorDetail withViolations(List<FieldViolation> violations) {
        return new ApiErrorDetail(timestamp, status, error, code, message, path, correlationId, violations);
    }
}

