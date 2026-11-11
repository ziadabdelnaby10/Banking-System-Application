package org.bank.account.service.implementation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bank.account.event.AccountEvents;
import org.bank.account.mapper.AccountMapper;
import org.bank.account.mapper.PageResponses;
import org.bank.account.model.Account;
import org.bank.account.model.AccountStatusHistory;
import org.bank.account.model.ProductView;
import org.bank.account.repository.AccountRepository;
import org.bank.account.repository.AccountSearchCriteria;
import org.bank.account.repository.AccountSpecifications;
import org.bank.account.repository.AccountStatusHistoryRepository;
import org.bank.account.service.AccountLookup;
import org.bank.account.service.AccountService;
import org.bank.account.service.OutboxRecorder;
import org.bank.account.service.ProductDirectory;
import org.bank.common.dto.enums.AccountStatus;
import org.bank.common.dto.request.ApplyBalanceSnapshotRequest;
import org.bank.common.dto.request.ChangeAccountStatusRequest;
import org.bank.common.dto.request.OpenAccountRequest;
import org.bank.common.dto.response.AccountBalanceResponse;
import org.bank.common.dto.response.AccountProductResponse;
import org.bank.common.dto.response.AccountResponse;
import org.bank.common.dto.response.AccountStatusHistoryResponse;
import org.bank.common.dto.response.AccountSummaryResponse;
import org.bank.common.dto.response.PageResponse;
import org.bank.common.exception.ResourceNotFoundException;
import org.bank.common.security.access.BranchAccessEvaluator;
import org.bank.common.security.correlation.CorrelationIds;
import org.bank.common.security.principal.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Query budget (no N+1 by construction: the entities have no associations and products come from the cache):
 * <ul>
 *   <li>search: 1 query for the page + 1 count (skipped on a short first page);</li>
 *   <li>get / balance: 1 query;</li>
 *   <li>freeze / unfreeze / close: 1 select + 1 update + 2 inserts (history, outbox);</li>
 *   <li>open: sequence + 3 inserts (account, history, outbox);</li>
 *   <li>status history: account + 1 page query + 1 count;</li>
 *   <li>balance snapshot: 1 conditional update.</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountServiceImpl implements AccountService {

    private static final String OPENING_REASON = "Account opened";

    private final AccountRepository accountRepository;
    private final AccountStatusHistoryRepository statusHistoryRepository;
    private final AccountMapper accountMapper;
    private final AccountLookup accountLookup;
    private final ProductDirectory productDirectory;
    private final BranchAccessEvaluator branchAccess;
    private final OutboxRecorder outbox;
    private final Clock clock;

    @Override
    @Transactional
    public AccountResponse open(OpenAccountRequest request) {
        String productCode = request.productCode().trim().toUpperCase(Locale.ROOT);
        ProductView product = productDirectory.catalog().findByCode(productCode)
                .orElseThrow(() -> new ResourceNotFoundException("Account product", productCode));

        // The customer id and branch are trusted from the caller: the branch scope is enforced at the controller and
        // there is no Customer-service lookup yet. Seam for validating the customer once such an API exists.
        OffsetDateTime now = OffsetDateTime.now(clock);
        Account account = accountRepository.save(Account.open(accountRepository.nextAccountSequence(),
                request.customerId(), request.branchCode(), product, request.currency(), now));

        UUID actor = CurrentUser.require().userId();
        statusHistoryRepository.save(AccountStatusHistory.record(account.getId(), null, account.getStatus(),
                OPENING_REASON, actor, now, CorrelationIds.current()));
        outbox.record(AccountEvents.ACCOUNT_AGGREGATE, account.getId(), AccountEvents.ACCOUNT_OPENED,
                new AccountEvents.AccountOpened(account.getId(), account.getAccountNo(), account.getCustomerId(),
                        account.getBranchCode(), product.code(), account.getCurrency(), actor));
        log.info("AUDIT account-opened actor={} account={} customer={} branch={} product={}", actor, account.getId(),
                account.getCustomerId(), account.getBranchCode(), product.code());
        return accountMapper.toResponse(account, product);
    }

    @Override
    public PageResponse<AccountSummaryResponse> search(AccountSearchCriteria criteria, Pageable pageable) {
        Page<Account> page = accountRepository.findAll(
                AccountSpecifications.matching(criteria, branchAccess.restrictedBranch()), pageable);
        ProductDirectory.Catalog catalog = productDirectory.catalog();
        return PageResponses.from(page, account -> accountMapper.toSummary(account, productOf(catalog, account)));
    }

    @Override
    public AccountResponse get(UUID accountId) {
        return detail(accountLookup.requireVisible(accountId));
    }

    @Override
    public AccountBalanceResponse getBalance(UUID accountId) {
        return accountMapper.toBalance(accountLookup.requireVisible(accountId));
    }

    @Override
    @Transactional
    public AccountResponse freeze(UUID accountId, ChangeAccountStatusRequest request) {
        return changeStatus(accountId, request, (account, now) -> account.freeze());
    }

    @Override
    @Transactional
    public AccountResponse unfreeze(UUID accountId, ChangeAccountStatusRequest request) {
        return changeStatus(accountId, request, (account, now) -> account.unfreeze());
    }

    @Override
    @Transactional
    public AccountResponse close(UUID accountId, ChangeAccountStatusRequest request) {
        return changeStatus(accountId, request, Account::close);
    }

    @Override
    public PageResponse<AccountStatusHistoryResponse> statusHistory(UUID accountId, Pageable pageable) {
        accountLookup.requireVisible(accountId);
        return PageResponses.from(statusHistoryRepository.findByAccountId(accountId, pageable), accountMapper::toHistory);
    }

    @Override
    public List<AccountProductResponse> listProducts() {
        return productDirectory.catalog().all().stream().map(accountMapper::toProduct).toList();
    }

    @Override
    @Transactional
    public void applyBalanceSnapshot(ApplyBalanceSnapshotRequest snapshot) {
        BigDecimal ledgerBalance = Account.toMoney(snapshot.ledgerBalance());
        BigDecimal availableBalance = Account.toMoney(snapshot.availableBalance());
        int applied = accountRepository.applyBalanceSnapshot(snapshot.accountId(), ledgerBalance, availableBalance,
                snapshot.balanceVersion(), OffsetDateTime.now(clock));
        if (applied == 0) {
            if (!accountRepository.existsById(snapshot.accountId())) {
                throw new ResourceNotFoundException("Account", snapshot.accountId());
            }
            log.debug("Ignored stale balance snapshot account={} version={}", snapshot.accountId(), snapshot.balanceVersion());
        }
    }

    private AccountResponse changeStatus(UUID accountId, ChangeAccountStatusRequest request,
                                         BiFunction<Account, OffsetDateTime, AccountStatus> action) {
        Account account = accountLookup.requireVisible(accountId);
        UUID actor = CurrentUser.require().userId();
        OffsetDateTime now = OffsetDateTime.now(clock);

        // Seam for ADR-005: freezing or closing an account is a high-risk action; routing it through the Approval
        // service (maker-checker) plugs in here once that service exists.
        AccountStatus previous = action.apply(account, now);
        // Flush so the audit columns and version in the response reflect this change.
        accountRepository.saveAndFlush(account);

        statusHistoryRepository.save(AccountStatusHistory.record(accountId, previous, account.getStatus(),
                request.reason(), actor, now, CorrelationIds.current()));
        outbox.record(AccountEvents.ACCOUNT_AGGREGATE, accountId, AccountEvents.ACCOUNT_STATUS_CHANGED,
                new AccountEvents.AccountStatusChanged(accountId, account.getAccountNo(), account.getBranchCode(),
                        previous.name(), account.getStatus().name(), request.reason().trim(), actor));
        log.info("AUDIT account-status-changed actor={} account={} from={} to={}", actor, accountId, previous,
                account.getStatus());
        return detail(account);
    }

    private AccountResponse detail(Account account) {
        return accountMapper.toResponse(account, productOf(productDirectory.catalog(), account));
    }

    private ProductView productOf(ProductDirectory.Catalog catalog, Account account) {
        return catalog.find(account.getProductId()).orElseGet(() -> productDirectory.loadFresh(account.getProductId()));
    }
}
