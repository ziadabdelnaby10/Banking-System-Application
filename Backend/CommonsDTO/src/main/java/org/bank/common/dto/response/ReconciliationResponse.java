package org.bank.common.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Result of recomputing an account's balance from its entries (credits minus debits) and comparing it with the
 * stored balance. {@code discrepancy} is stored minus computed and is zero when the account is consistent.
 */
public record ReconciliationResponse(
        UUID accountId,
        String currency,
        BigDecimal storedBalance,
        BigDecimal computedBalance,
        BigDecimal discrepancy,
        boolean consistent,
        OffsetDateTime checkedAt
) {
}
