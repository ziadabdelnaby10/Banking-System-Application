package org.bank.account.repository;

import jakarta.validation.constraints.Size;
import org.bank.common.dto.enums.AccountStatus;

import java.util.UUID;

/** Optional list filters, bound from query-string parameters. */
public record AccountSearchCriteria(
        UUID customerId,
        AccountStatus status,
        @Size(max = 20) String branchCode,
        /** Matches the start of the account number. */
        @Size(max = 30) String accountNo) {
}
