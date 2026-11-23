package org.bank.ledger.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.bank.common.dto.request.PostJournalBatchRequest;
import org.bank.common.dto.response.JournalBatchResponse;
import org.bank.common.dto.response.LedgerBalanceResponse;
import org.bank.common.dto.response.LedgerEntryResponse;
import org.bank.common.dto.response.PageResponse;
import org.bank.common.dto.response.ReconciliationResponse;
import org.bank.ledger.config.OpenApiConfig;
import org.bank.ledger.repository.LedgerEntrySearchCriteria;
import org.bank.ledger.service.LedgerService;
import org.bank.ledger.service.PostingOutcome;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ledger")
@Tag(name = "Ledger", description = "Double-entry journal, ledger entries, balances and reconciliation. Requires a bearer "
        + "token; the permission needed is listed per operation.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class LedgerController {

    private static final Set<String> ENTRY_SORTABLE = Set.of("postedAt", "amount");
    // Matches the (account_id, posted_at desc, id desc) index, so the default statement needs no sort step.
    private static final Sort ENTRY_DEFAULT_SORT = Sort.by(Sort.Order.desc("postedAt"), Sort.Order.desc("id"));

    private final LedgerService ledgerService;

    @PostMapping("/journal-batches")
    @Operation(summary = "Post a journal batch",
            description = "Permission: LEDGER_POST. Entries must balance (debits = credits) in every currency and are posted "
                    + "atomically together with the balance changes. Customer accounts cannot be overdrawn. 'batchRef' is the "
                    + "idempotency key: 201 with a Location header on first post, 200 with the original batch when replayed "
                    + "with identical content, 409 when reused with different content.")
    @PreAuthorize("hasAuthority('LEDGER_POST')")
    public ResponseEntity<JournalBatchResponse> post(@Valid @RequestBody PostJournalBatchRequest request) {
        PostingOutcome outcome = ledgerService.postBatch(request);
        if (!outcome.created()) {
            return ResponseEntity.ok(outcome.batch());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(outcome.batch().id()).toUri();
        return ResponseEntity.created(location).body(outcome.batch());
    }

    @GetMapping("/journal-batches/{id}")
    @Operation(summary = "Get a journal batch by id", description = "Permission: LEDGER_VIEW. Entries are in posting order.")
    @PreAuthorize("hasAuthority('LEDGER_VIEW')")
    public JournalBatchResponse getBatch(@PathVariable UUID id) {
        return ledgerService.getBatch(id);
    }

    @GetMapping("/accounts/{id}/entries")
    @Operation(summary = "Get an account statement",
            description = "Permission: LEDGER_VIEW. Newest first. Optional filters: entryType, from (inclusive), to (exclusive, "
                    + "ISO-8601 date-time). Page size is capped at 100. Sortable fields: postedAt, amount.")
    @PreAuthorize("hasAuthority('LEDGER_VIEW')")
    public PageResponse<LedgerEntryResponse> entries(@PathVariable UUID id,
                                                     @ParameterObject LedgerEntrySearchCriteria criteria,
                                                     @ParameterObject Pageable pageable) {
        return ledgerService.entries(id, criteria, SafePageable.restrict(pageable, ENTRY_SORTABLE, ENTRY_DEFAULT_SORT));
    }

    @GetMapping("/accounts/{id}/balance")
    @Operation(summary = "Get an account's ledger balance",
            description = "Permission: LEDGER_VIEW. The ledger owns balances; 'balanceVersion' increases on every posting.")
    @PreAuthorize("hasAuthority('LEDGER_VIEW')")
    public LedgerBalanceResponse balance(@PathVariable UUID id) {
        return ledgerService.getBalance(id);
    }

    @GetMapping("/accounts/{id}/reconciliation")
    @Operation(summary = "Reconcile an account",
            description = "Permission: LEDGER_RECONCILE. Recomputes the balance from all entries (credits minus debits) and "
                    + "compares it with the stored balance. Cost grows with the number of entries of the account.")
    @PreAuthorize("hasAuthority('LEDGER_RECONCILE')")
    public ReconciliationResponse reconcile(@PathVariable UUID id) {
        return ledgerService.reconcile(id);
    }
}
