package org.bank.account.service;

import org.bank.account.repository.AccountSearchCriteria;
import org.bank.common.dto.request.ApplyBalanceSnapshotRequest;
import org.bank.common.dto.request.ChangeAccountStatusRequest;
import org.bank.common.dto.request.OpenAccountRequest;
import org.bank.common.dto.response.AccountBalanceResponse;
import org.bank.common.dto.response.AccountProductResponse;
import org.bank.common.dto.response.AccountResponse;
import org.bank.common.dto.response.AccountStatusHistoryResponse;
import org.bank.common.dto.response.AccountSummaryResponse;
import org.bank.common.dto.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface AccountService {

    AccountResponse open(OpenAccountRequest request);

    PageResponse<AccountSummaryResponse> search(AccountSearchCriteria criteria, Pageable pageable);

    AccountResponse get(UUID accountId);

    AccountBalanceResponse getBalance(UUID accountId);

    AccountResponse freeze(UUID accountId, ChangeAccountStatusRequest request);

    AccountResponse unfreeze(UUID accountId, ChangeAccountStatusRequest request);

    AccountResponse close(UUID accountId, ChangeAccountStatusRequest request);

    PageResponse<AccountStatusHistoryResponse> statusHistory(UUID accountId, Pageable pageable);

    List<AccountProductResponse> listProducts();

    /**
     * Mirrors a Ledger balance posting into the read-model. Internal entry point (no REST endpoint): the Ledger
     * owns balances, and stale or duplicated snapshots are ignored.
     */
    void applyBalanceSnapshot(ApplyBalanceSnapshotRequest snapshot);
}
