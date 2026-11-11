package org.bank.common.dto.response;

import org.bank.common.dto.enums.AccountStatus;
import org.bank.common.dto.enums.AccountType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Lightweight row used by account lists. */
public record AccountSummaryResponse(
        UUID id,
        String accountNo,
        UUID customerId,
        String branchCode,
        String productCode,
        AccountType accountType,
        String currency,
        AccountStatus status,
        BigDecimal availableBalance,
        OffsetDateTime openedAt
) {
}
