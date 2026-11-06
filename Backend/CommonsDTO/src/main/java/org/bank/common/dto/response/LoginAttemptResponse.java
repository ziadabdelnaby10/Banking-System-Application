package org.bank.common.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record LoginAttemptResponse(
        UUID id,
        UUID userId,
        String username,
        String ipAddress,
        String userAgent,
        String result,
        String failureReason,
        String correlationId,
        OffsetDateTime createdAt
) {
}
