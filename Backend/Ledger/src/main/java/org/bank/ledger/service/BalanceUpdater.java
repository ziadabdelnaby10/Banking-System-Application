package org.bank.ledger.service;

import lombok.RequiredArgsConstructor;
import org.bank.common.exception.BusinessRuleException;
import org.bank.common.util.UuidV7Generator;
import org.bank.ledger.config.LedgerProperties;
import org.bank.ledger.model.AccountBalance;
import org.bank.ledger.repository.AccountBalanceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Applies a batch's net deltas to the account balances inside the caller's transaction.
 *
 * <p>Statements per account: one conditional UPDATE in the common case (the account exists). The first posting to a
 * new account adds an insert-if-absent of its zero row and a second UPDATE. All affected rows are then read back
 * with a single query. Callers pass deltas in ascending account order (see {@link BalanceDeltas}).
 */
@Component
@RequiredArgsConstructor
public class BalanceUpdater {

    private final AccountBalanceRepository balances;
    private final LedgerProperties properties;

    /**
     * @return the refreshed balance of every account that was changed
     * @throws BusinessRuleException on insufficient funds for a customer account or a currency mismatch
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<AccountBalance> apply(List<AccountDelta> deltas, OffsetDateTime now) {
        for (AccountDelta delta : deltas) {
            apply(delta, now);
        }
        return deltas.isEmpty() ? List.of()
                : balances.findByAccountIdIn(deltas.stream().map(AccountDelta::accountId).toList());
    }

    private void apply(AccountDelta delta, OffsetDateTime now) {
        if (balances.applyDelta(delta.accountId(), delta.currency(), delta.net(), now) == 1) {
            return;
        }
        balances.insertIfAbsent(UuidV7Generator.generateUUIDV7(), delta.accountId(),
                properties.kindOf(delta.accountId()).name(), delta.currency(), now);
        if (balances.applyDelta(delta.accountId(), delta.currency(), delta.net(), now) == 0) {
            throw rejection(delta);
        }
    }

    private BusinessRuleException rejection(AccountDelta delta) {
        return balances.findCurrencyByAccountId(delta.accountId())
                .filter(currency -> !currency.equals(delta.currency()))
                .map(currency -> new BusinessRuleException("Account " + delta.accountId() + " is held in " + currency
                        + ", not " + delta.currency()))
                .orElseGet(() -> new BusinessRuleException("Insufficient funds on account " + delta.accountId()));
    }
}
