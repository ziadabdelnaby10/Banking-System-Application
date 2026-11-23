package org.bank.ledger.service;

import org.bank.common.dto.request.PostJournalBatchRequest;
import org.bank.common.dto.response.JournalBatchResponse;
import org.bank.common.dto.response.LedgerBalanceResponse;
import org.bank.common.dto.response.LedgerEntryResponse;
import org.bank.common.dto.response.PageResponse;
import org.bank.common.dto.response.ReconciliationResponse;
import org.bank.ledger.repository.LedgerEntrySearchCriteria;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface LedgerService {

    /**
     * Posts a balanced batch atomically: entries, balance changes and outbox events commit together or not at all.
     * Idempotent on the batch reference.
     */
    PostingOutcome postBatch(PostJournalBatchRequest request);

    JournalBatchResponse getBatch(UUID batchId);

    PageResponse<LedgerEntryResponse> entries(UUID accountId, LedgerEntrySearchCriteria criteria, Pageable pageable);

    LedgerBalanceResponse getBalance(UUID accountId);

    /** Recomputes the account balance from its entries and compares it with the stored balance. */
    ReconciliationResponse reconcile(UUID accountId);
}
