package org.bank.common.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Balance read-model update originating from a Ledger posting. Not exposed over REST: the Ledger owns the
 * balances and Account only mirrors them. {@code balanceVersion} is Ledger's monotonic sequence and lets Account
 * ignore out-of-order or duplicated deliveries.
 */
public record ApplyBalanceSnapshotRequest(
        @NotNull UUID accountId,
        @NotNull @PositiveOrZero BigDecimal ledgerBalance,
        @NotNull @PositiveOrZero BigDecimal availableBalance,
        @Min(1) long balanceVersion) {
}
