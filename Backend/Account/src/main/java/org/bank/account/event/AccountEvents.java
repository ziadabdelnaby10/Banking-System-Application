package org.bank.account.event;

import java.util.UUID;

/** Outbox event names and payloads. Payloads carry ids and codes only: no balances, no personal data. */
public final class AccountEvents {

    public static final String ACCOUNT_AGGREGATE = "Account";

    public static final String ACCOUNT_OPENED = "account.opened";
    public static final String ACCOUNT_STATUS_CHANGED = "account.status-changed";

    private AccountEvents() {
    }

    public record AccountOpened(UUID accountId, String accountNo, UUID customerId, String branchCode,
                                String productCode, String currency, UUID openedBy) {
    }

    public record AccountStatusChanged(UUID accountId, String accountNo, String branchCode,
                                       String fromStatus, String toStatus, String reason, UUID changedBy) {
    }
}
