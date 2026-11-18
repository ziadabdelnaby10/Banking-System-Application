package org.bank.common.dto.response;

import org.bank.common.dto.enums.AccountStatus;
import org.bank.common.dto.enums.AccountType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Full account view with audit metadata. Balances are a read-model of the Ledger. */
public record AccountResponse(
        UUID id,
        String accountNo,
        UUID customerId,
        String branchCode,
        String productCode,
        String productName,
        AccountType accountType,
        String currency,
        AccountStatus status,
        BigDecimal ledgerBalance,
        BigDecimal availableBalance,
        OffsetDateTime openedAt,
        OffsetDateTime closedAt,
        OffsetDateTime createdAt,
        UUID createdBy,
        OffsetDateTime updatedAt,
        UUID updatedBy,
        Long version
) {
}
