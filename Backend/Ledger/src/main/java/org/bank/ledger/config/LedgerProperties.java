package org.bank.ledger.config;

import org.bank.common.dto.enums.LedgerAccountKind;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;
import java.util.UUID;

/**
 * Ledger settings under {@code bank.ledger}.
 *
 * @param systemAccounts ids of the bank's own accounts (cash, clearing, fees). They may carry a negative balance;
 *                       every other account is a customer account and can never be overdrawn. The kind is fixed
 *                       when an account first appears in a posting.
 */
@ConfigurationProperties(prefix = "bank.ledger")
public record LedgerProperties(Set<UUID> systemAccounts) {

    public LedgerProperties {
        systemAccounts = systemAccounts == null ? Set.of() : Set.copyOf(systemAccounts);
    }

    public LedgerAccountKind kindOf(UUID accountId) {
        return systemAccounts.contains(accountId) ? LedgerAccountKind.SYSTEM : LedgerAccountKind.CUSTOMER;
    }
}
