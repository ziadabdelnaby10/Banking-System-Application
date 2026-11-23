package org.bank.ledger.service;

import org.bank.common.dto.request.JournalEntryRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Collapses a batch into one net delta per account, in ascending account-id order. Always locking accounts in the
 * same order is what keeps two concurrent postings that touch the same accounts from deadlocking.
 */
public final class BalanceDeltas {

    private BalanceDeltas() {
    }

    public static List<AccountDelta> of(List<JournalEntryRequest> entries) {
        Map<UUID, AccountDelta> byAccount = new TreeMap<>();
        for (JournalEntryRequest entry : entries) {
            BigDecimal signed = JournalBatchValidator.signed(entry);
            byAccount.merge(entry.accountId(), new AccountDelta(entry.accountId(), entry.currency(), signed),
                    (left, right) -> new AccountDelta(left.accountId(), left.currency(), left.net().add(right.net())));
        }
        // An account whose debits and credits cancel out within the batch needs no balance change.
        return byAccount.values().stream().filter(delta -> delta.net().signum() != 0).toList();
    }
}
