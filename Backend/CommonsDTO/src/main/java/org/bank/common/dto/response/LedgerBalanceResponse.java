package org.bank.common.dto.response;

import org.bank.common.dto.enums.LedgerAccountKind;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Authoritative balance of an account. {@code balanceVersion} increases by one on every posting that touches it. */
public record LedgerBalanceResponse(
        UUID accountId,
        String currency,
        LedgerAccountKind accountKind,
        BigDecimal ledgerBalance,
        BigDecimal availableBalance,
        long balanceVersion,
        OffsetDateTime asOf
) {
}
