package org.bank.ledger.service;

import org.bank.common.dto.enums.EntryType;
import org.bank.common.dto.request.JournalEntryRequest;
import org.bank.common.dto.request.PostJournalBatchRequest;
import org.bank.common.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The double-entry invariants of a batch, checked before anything touches the database: debits equal credits in
 * every currency, and an account is never used in two currencies. Bean validation has already guaranteed positive
 * amounts with at most two decimals and 2..200 entries.
 */
@Component
public class JournalBatchValidator {

    public void validate(PostJournalBatchRequest request) {
        List<JournalEntryRequest> entries = request.entries();
        Map<UUID, String> currencyByAccount = HashMap.newHashMap(entries.size());
        Map<String, BigDecimal> imbalanceByCurrency = new HashMap<>();

        for (JournalEntryRequest entry : entries) {
            String previous = currencyByAccount.putIfAbsent(entry.accountId(), entry.currency());
            if (previous != null && !previous.equals(entry.currency())) {
                throw new BusinessRuleException("Account " + entry.accountId() + " is used in more than one currency");
            }
            imbalanceByCurrency.merge(entry.currency(), signed(entry), BigDecimal::add);
        }

        for (Map.Entry<String, BigDecimal> imbalance : imbalanceByCurrency.entrySet()) {
            if (imbalance.getValue().signum() != 0) {
                throw new BusinessRuleException("Batch is not balanced in " + imbalance.getKey()
                        + ": debits and credits differ by " + imbalance.getValue().abs().toPlainString());
            }
        }
    }

    static BigDecimal signed(JournalEntryRequest entry) {
        return entry.entryType() == EntryType.CREDIT ? entry.amount() : entry.amount().negate();
    }
}
