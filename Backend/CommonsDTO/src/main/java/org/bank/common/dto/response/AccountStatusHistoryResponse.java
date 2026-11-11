package org.bank.common.dto.response;

import org.bank.common.dto.enums.AccountStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One entry of the append-only account status trail. {@code previousStatus} is null for the opening entry. */
public record AccountStatusHistoryResponse(
        UUID id,
        UUID accountId,
        AccountStatus previousStatus,
        AccountStatus newStatus,
        String reason,
        UUID changedBy,
        OffsetDateTime changedAt
) {
}
