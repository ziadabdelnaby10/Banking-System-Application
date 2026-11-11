package org.bank.account.service;

import lombok.RequiredArgsConstructor;
import org.bank.account.model.Account;
import org.bank.account.repository.AccountRepository;
import org.bank.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Loads an account the caller is allowed to see (one query); anything else is "not found". */
@Component
@RequiredArgsConstructor
public class AccountLookup {

    private final AccountRepository accounts;
    private final BranchScopePolicy scopePolicy;

    public Account requireVisible(UUID accountId) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        scopePolicy.requireVisible("Account", accountId, account.getBranchCode());
        return account;
    }
}
