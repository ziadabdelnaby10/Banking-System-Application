package org.bank.account.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.bank.account.config.OpenApiConfig;
import org.bank.account.repository.AccountSearchCriteria;
import org.bank.account.service.AccountService;
import org.bank.common.dto.request.ChangeAccountStatusRequest;
import org.bank.common.dto.request.OpenAccountRequest;
import org.bank.common.dto.response.AccountBalanceResponse;
import org.bank.common.dto.response.AccountResponse;
import org.bank.common.dto.response.AccountStatusHistoryResponse;
import org.bank.common.dto.response.AccountSummaryResponse;
import org.bank.common.dto.response.PageResponse;
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
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts", description = "Account opening, balances and lifecycle. Requires a bearer token; the permission needed is "
        + "listed per operation. Data is scoped to the caller's branch unless the caller holds a cross-branch role.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AccountController {

    private static final Set<String> SORTABLE = Set.of("accountNo", "createdAt", "openedAt", "status", "branchCode");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final Set<String> HISTORY_SORTABLE = Set.of("changedAt");
    private static final Sort HISTORY_DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "changedAt");

    private final AccountService accountService;

    @PostMapping
    @Operation(summary = "Open an account",
            description = "Permission: ACCOUNT_CREATE, and the branch must be one the caller may access. The product must exist and be "
                    + "active. The account starts ACTIVE with a zero balance. Returns 201 with a Location header.")
    @PreAuthorize("hasAuthority('ACCOUNT_CREATE') and @branchAccess.canAccess(#request.branchCode())")
    public ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
        AccountResponse created = accountService.open(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "Search accounts",
            description = "Permission: ACCOUNT_VIEW. 'accountNo' matches the start of the account number. Branch-restricted callers "
                    + "only see their own branch. Page size is capped at 100. Sortable fields: accountNo, createdAt, openedAt, status, branchCode.")
    @PreAuthorize("hasAuthority('ACCOUNT_VIEW')")
    public PageResponse<AccountSummaryResponse> search(@ParameterObject @Valid AccountSearchCriteria criteria,
                                                       @ParameterObject Pageable pageable) {
        return accountService.search(criteria, SafePageable.restrict(pageable, SORTABLE, DEFAULT_SORT));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an account by id",
            description = "Permission: ACCOUNT_VIEW. Accounts outside the caller's branch are reported as not found.")
    @PreAuthorize("hasAuthority('ACCOUNT_VIEW')")
    public AccountResponse get(@PathVariable UUID id) {
        return accountService.get(id);
    }

    @GetMapping("/{id}/balance")
    @Operation(summary = "Get an account's balance",
            description = "Permission: ACCOUNT_VIEW. The balance is a read-model of the Ledger; 'asOf' is when it was last refreshed.")
    @PreAuthorize("hasAuthority('ACCOUNT_VIEW')")
    public AccountBalanceResponse balance(@PathVariable UUID id) {
        return accountService.getBalance(id);
    }

    @GetMapping("/{id}/status-history")
    @Operation(summary = "Get an account's status history",
            description = "Permission: ACCOUNT_VIEW. Newest first. Sortable field: changedAt.")
    @PreAuthorize("hasAuthority('ACCOUNT_VIEW')")
    public PageResponse<AccountStatusHistoryResponse> statusHistory(@PathVariable UUID id, @ParameterObject Pageable pageable) {
        return accountService.statusHistory(id, SafePageable.restrict(pageable, HISTORY_SORTABLE, HISTORY_DEFAULT_SORT));
    }

    @PostMapping("/{id}/freeze")
    @Operation(summary = "Freeze an account",
            description = "Permission: ACCOUNT_STATUS_CHANGE. Only ACTIVE accounts can be frozen. A reason is mandatory and recorded "
                    + "in the status history.")
    @PreAuthorize("hasAuthority('ACCOUNT_STATUS_CHANGE')")
    public AccountResponse freeze(@PathVariable UUID id, @Valid @RequestBody ChangeAccountStatusRequest request) {
        return accountService.freeze(id, request);
    }

    @PostMapping("/{id}/unfreeze")
    @Operation(summary = "Unfreeze an account",
            description = "Permission: ACCOUNT_STATUS_CHANGE. Only FROZEN accounts can be unfrozen. A reason is mandatory and recorded "
                    + "in the status history.")
    @PreAuthorize("hasAuthority('ACCOUNT_STATUS_CHANGE')")
    public AccountResponse unfreeze(@PathVariable UUID id, @Valid @RequestBody ChangeAccountStatusRequest request) {
        return accountService.unfreeze(id, request);
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Close an account",
            description = "Permission: ACCOUNT_STATUS_CHANGE. The balance must be zero. CLOSED is final. A reason is mandatory and "
                    + "recorded in the status history.")
    @PreAuthorize("hasAuthority('ACCOUNT_STATUS_CHANGE')")
    public AccountResponse close(@PathVariable UUID id, @Valid @RequestBody ChangeAccountStatusRequest request) {
        return accountService.close(id, request);
    }
}
