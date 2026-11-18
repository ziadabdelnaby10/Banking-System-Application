package org.bank.common.dto.response;

import org.bank.common.dto.enums.AccountStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Balance view of an account. {@code asOf} is the time the balance snapshot was last refreshed. */
public record AccountBalanceResponse(
        UUID accountId,
        String accountNo,
        String currency,
        AccountStatus status,
        BigDecimal ledgerBalance,
        BigDecimal availableBalance,
        OffsetDateTime asOf
) {
}
