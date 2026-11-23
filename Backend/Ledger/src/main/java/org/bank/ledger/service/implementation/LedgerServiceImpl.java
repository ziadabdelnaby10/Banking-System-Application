package org.bank.ledger.service.implementation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bank.common.dto.enums.LedgerAccountKind;
import org.bank.common.dto.request.ApplyBalanceSnapshotRequest;
import org.bank.common.dto.request.JournalEntryRequest;
import org.bank.common.dto.request.PostJournalBatchRequest;
import org.bank.common.dto.response.JournalBatchResponse;
import org.bank.common.dto.response.LedgerBalanceResponse;
import org.bank.common.dto.response.LedgerEntryResponse;
import org.bank.common.dto.response.PageResponse;
import org.bank.common.dto.response.ReconciliationResponse;
import org.bank.common.error.ErrorCode;
import org.bank.common.exception.BankException;
import org.bank.common.exception.DuplicateResourceException;
import org.bank.common.exception.ResourceNotFoundException;
import org.bank.common.security.correlation.CorrelationIds;
import org.bank.common.security.principal.CurrentUser;
import org.bank.ledger.event.LedgerEvents;
import org.bank.ledger.mapper.LedgerMapper;
import org.bank.ledger.mapper.PageResponses;
import org.bank.ledger.model.AccountBalance;
import org.bank.ledger.model.JournalBatch;
import org.bank.ledger.model.LedgerEntry;
import org.bank.ledger.repository.AccountBalanceRepository;
import org.bank.ledger.repository.JournalBatchRepository;
import org.bank.ledger.repository.LedgerEntryRepository;
import org.bank.ledger.repository.LedgerEntrySearchCriteria;
import org.bank.ledger.repository.LedgerEntrySpecifications;
import org.bank.ledger.repository.ReconciliationRow;
import org.bank.ledger.service.BalanceDeltas;
import org.bank.ledger.service.BalanceUpdater;
import org.bank.ledger.service.BatchFingerprint;
import org.bank.ledger.service.JournalBatchValidator;
import org.bank.ledger.service.LedgerService;
import org.bank.ledger.service.OutboxRecorder;
import org.bank.ledger.service.PostingOutcome;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Query budget (no N+1 by construction: the entities have no associations):
 * <ul>
 *   <li>post: 1 idempotency lookup + 1 batch insert + 1 conditional update per touched account (2 extra statements
 *   for an account's first posting) + 1 read-back of the balances + batched inserts of the entries and outbox
 *   rows (JDBC batch size 50); an idempotent replay is 2 selects;</li>
 *   <li>get batch: 2 selects (header, entries);</li>
 *   <li>statement page: 1 existence check + 1 page query + 1 count (skipped on a short first page);</li>
 *   <li>balance: 1 select; reconcile: 1 select (balance and entry sum in one statement).</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LedgerServiceImpl implements LedgerService {

    private final JournalBatchRepository batchRepository;
    private final LedgerEntryRepository entryRepository;
    private final AccountBalanceRepository balanceRepository;
    private final LedgerMapper ledgerMapper;
    private final JournalBatchValidator validator;
    private final BatchFingerprint fingerprint;
    private final BalanceUpdater balanceUpdater;
    private final OutboxRecorder outbox;
    private final Clock clock;

    @Override
    @Transactional
    public PostingOutcome postBatch(PostJournalBatchRequest request) {
        validator.validate(request);
        String batchRef = request.batchRef().trim();
        String requestHash = fingerprint.of(request);

        JournalBatch existing = batchRepository.findByBatchRef(batchRef).orElse(null);
        if (existing != null) {
            return replay(existing, requestHash);
        }

        UUID actor = CurrentUser.require().userId();
        OffsetDateTime now = OffsetDateTime.now(clock);

        // Flushed immediately: a concurrent post of the same reference blocks on the unique index here, before any
        // balance is touched, and is then rejected as a conflict.
        JournalBatch batch = batchRepository.saveAndFlush(JournalBatch.post(batchRef, request.sourceService().trim(),
                blankToNull(request.sourceEvent()), requestHash, CorrelationIds.current(), actor, now));

        List<AccountBalance> changed = balanceUpdater.apply(BalanceDeltas.of(request.entries()), now);
        List<LedgerEntry> lines = entryRepository.saveAll(toEntries(batch.getId(), request.entries(), now));

        outbox.record(LedgerEvents.BATCH_AGGREGATE, batch.getId(), LedgerEvents.BATCH_POSTED,
                new LedgerEvents.BatchPosted(batch.getId(), batchRef, batch.getSourceService(), batch.getSourceEvent(),
                        lines.size(), actor));
        recordBalanceSnapshots(changed);

        log.info("AUDIT ledger-batch-posted actor={} batch={} ref={} source={} entries={} accounts={}", actor,
                batch.getId(), batchRef, batch.getSourceService(), lines.size(), changed.size());
        return new PostingOutcome(ledgerMapper.toResponse(batch, lines), true);
    }

    @Override
    public JournalBatchResponse getBatch(UUID batchId) {
        JournalBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Journal batch", batchId));
        return withEntries(batch);
    }

    @Override
    public PageResponse<LedgerEntryResponse> entries(UUID accountId, LedgerEntrySearchCriteria criteria, Pageable pageable) {
        if (criteria.from() != null && criteria.to() != null && !criteria.from().isBefore(criteria.to())) {
            throw new BankException(ErrorCode.BAD_REQUEST, "'from' must be before 'to'");
        }
        requireKnownAccount(accountId);
        Page<LedgerEntry> page = entryRepository.findAll(LedgerEntrySpecifications.matching(accountId, criteria), pageable);
        return PageResponses.from(page, ledgerMapper::toEntry);
    }

    @Override
    public LedgerBalanceResponse getBalance(UUID accountId) {
        return balanceRepository.findByAccountId(accountId)
                .map(ledgerMapper::toBalance)
                .orElseThrow(() -> new ResourceNotFoundException("Ledger account", accountId));
    }

    @Override
    public ReconciliationResponse reconcile(UUID accountId) {
        ReconciliationRow row = balanceRepository.reconcile(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Ledger account", accountId));
        BigDecimal discrepancy = row.getStored().subtract(row.getComputed());
        boolean consistent = discrepancy.signum() == 0;
        if (!consistent) {
            log.error("AUDIT ledger-reconciliation-mismatch account={} stored={} computed={}", accountId,
                    row.getStored(), row.getComputed());
        }
        return new ReconciliationResponse(accountId, row.getCurrency(), row.getStored(), row.getComputed(), discrepancy,
                consistent, OffsetDateTime.now(clock));
    }

    private PostingOutcome replay(JournalBatch existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new DuplicateResourceException("Batch reference " + existing.getBatchRef()
                    + " was already used by a different request");
        }
        return new PostingOutcome(withEntries(existing), false);
    }

    private JournalBatchResponse withEntries(JournalBatch batch) {
        return ledgerMapper.toResponse(batch, entryRepository.findByBatchIdOrderByLineNoAsc(batch.getId()));
    }

    private static List<LedgerEntry> toEntries(UUID batchId, List<JournalEntryRequest> requests, OffsetDateTime now) {
        List<LedgerEntry> lines = new ArrayList<>(requests.size());
        int lineNo = 1;
        for (JournalEntryRequest request : requests) {
            lines.add(LedgerEntry.create(batchId, lineNo++, request.accountId(), request.entryType(), request.amount(),
                    request.currency(), blankToNull(request.reference()), now));
        }
        return lines;
    }

    /** Only customer balances are mirrored by Account; the bank's own accounts stay inside the ledger. */
    private void recordBalanceSnapshots(List<AccountBalance> changed) {
        for (AccountBalance balance : changed) {
            if (balance.getAccountKind() == LedgerAccountKind.CUSTOMER) {
                outbox.record(LedgerEvents.BALANCE_AGGREGATE, balance.getAccountId(), LedgerEvents.BALANCE_UPDATED,
                        new ApplyBalanceSnapshotRequest(balance.getAccountId(), balance.getLedgerBalance(),
                                balance.getAvailableBalance(), balance.getBalanceVersion()));
            }
        }
    }

    private void requireKnownAccount(UUID accountId) {
        if (!balanceRepository.existsByAccountId(accountId)) {
            throw new ResourceNotFoundException("Ledger account", accountId);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
