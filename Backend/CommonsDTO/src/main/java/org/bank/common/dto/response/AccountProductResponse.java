package org.bank.common.dto.response;

import org.bank.common.dto.enums.AccountType;
import java.math.BigDecimal;
import java.util.UUID;

/** Account product catalogue entry. */
public record AccountProductResponse(
        UUID id,
        String code,
        String name,
        AccountType accountType,
        BigDecimal minBalance,
        boolean active
) {
}
