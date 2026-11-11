package org.bank.account.service.implementation;

import org.bank.account.event.AccountEvents;
import org.bank.account.mapper.AccountMapper;
import org.bank.account.mapper.AccountMapperImpl;
import org.bank.account.model.Account;
import org.bank.account.model.AccountStatusHistory;
import org.bank.account.model.ProductView;
import org.bank.account.repository.AccountRepository;
import org.bank.account.repository.AccountSearchCriteria;
import org.bank.account.repository.AccountStatusHistoryRepository;
import org.bank.account.service.AccountLookup;
import org.bank.account.service.OutboxRecorder;
import org.bank.account.service.ProductDirectory;
import org.bank.common.dto.enums.AccountStatus;
import org.bank.common.dto.enums.AccountType;
import org.bank.common.dto.request.ApplyBalanceSnapshotRequest;
import org.bank.common.dto.request.ChangeAccountStatusRequest;
import org.bank.common.dto.request.OpenAccountRequest;
import org.bank.common.dto.response.AccountResponse;
import org.bank.common.dto.response.AccountSummaryResponse;
import org.bank.common.dto.response.PageResponse;
import org.bank.common.exception.BusinessRuleException;
import org.bank.common.exception.ResourceNotFoundException;
import org.bank.common.security.access.BranchAccessEvaluator;
import org.bank.common.security.principal.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    private static final Instant INSTANT = Instant.parse("2026-11-11T10:00:00Z");
    private static final OffsetDateTime NOW = OffsetDateTime.ofInstant(INSTANT, ZoneOffset.UTC);
    private static final UUID ACTOR = UUID.fromString("01900000-0000-7000-8000-0000000000b1");
    private static final UUID CUSTOMER = UUID.fromString("01900000-0000-7000-8000-0000000000c1");
    private static final ProductView SAVINGS = new ProductView(UUID.fromString("01900000-0000-7000-8000-0000000000a1"),
            "SAVINGS_STD", "Standard Savings", AccountType.SAVINGS, BigDecimal.ZERO, true);
    private static final ProductView RETIRED = new ProductView(UUID.fromString("01900000-0000-7000-8000-0000000000a9"),
            "OLD", "Retired", AccountType.CURRENT, BigDecimal.ZERO, false);
    private static final ProductDirectory.Catalog CATALOG = new ProductDirectory.Catalog(List.of(SAVINGS, RETIRED),
            Map.of(SAVINGS.id(), SAVINGS, RETIRED.id(), RETIRED), Map.of("SAVINGS_STD", SAVINGS, "OLD", RETIRED));

    @Mock
    private AccountRepository accounts;
    @Mock
    private AccountStatusHistoryRepository histories;
    @Mock
    private AccountLookup lookup;
    @Mock
    private ProductDirectory products;
    @Mock
    private BranchAccessEvaluator branchAccess;
    @Mock
    private OutboxRecorder outbox;

    private final AccountMapper mapper = new AccountMapperImpl();
    private AccountServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AccountServiceImpl(accounts, histories, mapper, lookup, products, branchAccess, outbox,
                Clock.fixed(INSTANT, ZoneOffset.UTC));
        AuthenticatedUser user = new AuthenticatedUser(ACTOR, "manager1", "BR001", Set.of("MANAGER"),
                Set.of("ACCOUNT_VIEW", "ACCOUNT_CREATE", "ACCOUNT_STATUS_CHANGE"), true, "sid");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(user, null));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static Account persisted(UUID productId) {
        ProductView product = productId.equals(SAVINGS.id()) ? SAVINGS : new ProductView(productId, "X", "X", AccountType.CURRENT, BigDecimal.ZERO, true);
        Account account = Account.open(7L, CUSTOMER, "BR001", product, "EGP", NOW);
        ReflectionTestUtils.invokeMethod(account, "onCreate");
        return account;
    }

    @Test
    void openCreatesAccountHistoryAndEvent() {
        when(products.catalog()).thenReturn(CATALOG);
        when(accounts.nextAccountSequence()).thenReturn(7L);
        when(accounts.save(any(Account.class))).thenAnswer(invocation -> {
            Account saved = invocation.getArgument(0);
            ReflectionTestUtils.invokeMethod(saved, "onCreate");
            return saved;
        });

        AccountResponse response = service.open(new OpenAccountRequest(CUSTOMER, "savings_std", "EGP", "br001"));

        ArgumentCaptor<AccountStatusHistory> history = ArgumentCaptor.forClass(AccountStatusHistory.class);
        verify(histories).save(history.capture());
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox).record(eq(AccountEvents.ACCOUNT_AGGREGATE), eq(response.id()), eq(AccountEvents.ACCOUNT_OPENED), payload.capture());
        AccountEvents.AccountOpened event = (AccountEvents.AccountOpened) payload.getValue();

        assertAll(
                () -> assertNotNull(response.id()),
                () -> assertEquals("ACC000000000007", response.accountNo()),
                () -> assertEquals("BR001", response.branchCode()),
                () -> assertEquals("SAVINGS_STD", response.productCode()),
                () -> assertEquals(AccountType.SAVINGS, response.accountType()),
                () -> assertEquals(AccountStatus.ACTIVE, response.status()),
                () -> assertNull(history.getValue().getPreviousStatus()),
                () -> assertEquals(AccountStatus.ACTIVE, history.getValue().getNewStatus()),
                () -> assertEquals(ACTOR, history.getValue().getChangedBy()),
                () -> assertEquals(NOW, history.getValue().getChangedAt()),
                () -> assertEquals(CUSTOMER, event.customerId()),
                () -> assertEquals(ACTOR, event.openedBy()),
                () -> assertEquals("SAVINGS_STD", event.productCode()));
    }

    @Test
    void openRejectsUnknownProductWithoutTouchingTheDatabase() {
        when(products.catalog()).thenReturn(CATALOG);

        assertAll(
                () -> assertThrowsExactly(ResourceNotFoundException.class,
                        () -> service.open(new OpenAccountRequest(CUSTOMER, "NOPE", "EGP", "BR001"))),
                () -> verify(accounts, never()).save(any(Account.class)),
                () -> verifyNoInteractions(histories, outbox));
    }

    @Test
    void openRejectsRetiredProduct() {
        when(products.catalog()).thenReturn(CATALOG);
        when(accounts.nextAccountSequence()).thenReturn(8L);

        assertAll(
                () -> assertThrowsExactly(BusinessRuleException.class,
                        () -> service.open(new OpenAccountRequest(CUSTOMER, "OLD", "EGP", "BR001"))),
                () -> verify(accounts, never()).save(any(Account.class)),
                () -> verifyNoInteractions(histories, outbox));
    }

    @Test
    void freezeRecordsHistoryAndEventWithTrimmedReason() {
        Account account = persisted(SAVINGS.id());
        when(lookup.requireVisible(account.getId())).thenReturn(account);
        when(products.catalog()).thenReturn(CATALOG);

        AccountResponse response = service.freeze(account.getId(), new ChangeAccountStatusRequest("  Fraud suspicion  "));

        ArgumentCaptor<AccountStatusHistory> history = ArgumentCaptor.forClass(AccountStatusHistory.class);
        verify(histories).save(history.capture());
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox).record(eq(AccountEvents.ACCOUNT_AGGREGATE), eq(account.getId()), eq(AccountEvents.ACCOUNT_STATUS_CHANGED), payload.capture());
        AccountEvents.AccountStatusChanged event = (AccountEvents.AccountStatusChanged) payload.getValue();

        assertAll(
                () -> assertEquals(AccountStatus.FROZEN, response.status()),
                () -> verify(accounts).saveAndFlush(account),
                () -> assertEquals(AccountStatus.ACTIVE, history.getValue().getPreviousStatus()),
                () -> assertEquals(AccountStatus.FROZEN, history.getValue().getNewStatus()),
                () -> assertEquals("Fraud suspicion", history.getValue().getReason()),
                () -> assertEquals("ACTIVE", event.fromStatus()),
                () -> assertEquals("FROZEN", event.toStatus()),
                () -> assertEquals("Fraud suspicion", event.reason()),
                () -> assertEquals(ACTOR, event.changedBy()));
    }

    @Test
    void closeStampsClosedAtFromTheClock() {
        Account account = persisted(SAVINGS.id());
        when(lookup.requireVisible(account.getId())).thenReturn(account);
        when(products.catalog()).thenReturn(CATALOG);

        AccountResponse response = service.close(account.getId(), new ChangeAccountStatusRequest("Customer request"));

        assertAll(
                () -> assertEquals(AccountStatus.CLOSED, response.status()),
                () -> assertEquals(NOW, response.closedAt()));
    }

    @Test
    void invalidTransitionLeavesNoHistoryOrEvent() {
        Account account = persisted(SAVINGS.id());
        when(lookup.requireVisible(account.getId())).thenReturn(account);

        assertAll(
                () -> assertThrowsExactly(BusinessRuleException.class,
                        () -> service.unfreeze(account.getId(), new ChangeAccountStatusRequest("Cleared"))),
                () -> verify(accounts, never()).saveAndFlush(any(Account.class)),
                () -> verifyNoInteractions(histories, outbox));
    }

    @Test
    void accountOutsideCallersBranchIsNotFoundAndNothingIsWritten() {
        UUID id = UUID.randomUUID();
        when(lookup.requireVisible(id)).thenThrow(new ResourceNotFoundException("Account", id));

        assertAll(
                () -> assertThrowsExactly(ResourceNotFoundException.class,
                        () -> service.freeze(id, new ChangeAccountStatusRequest("Reason"))),
                () -> assertThrowsExactly(ResourceNotFoundException.class, () -> service.getBalance(id)),
                () -> assertThrowsExactly(ResourceNotFoundException.class, () -> service.statusHistory(id, PageRequest.of(0, 10))),
                () -> verifyNoInteractions(histories, outbox));
    }

    @Test
    void getFallsBackToAFreshProductLoadWhenTheCachedCatalogIsStale() {
        UUID newProductId = UUID.fromString("01900000-0000-7000-8000-0000000000a5");
        Account account = persisted(newProductId);
        ProductView fresh = new ProductView(newProductId, "NEW_PRODUCT", "New", AccountType.CURRENT, BigDecimal.ZERO, true);
        when(lookup.requireVisible(account.getId())).thenReturn(account);
        when(products.catalog()).thenReturn(CATALOG);
        when(products.loadFresh(newProductId)).thenReturn(fresh);

        AccountResponse response = service.get(account.getId());

        assertAll(
                () -> assertEquals("NEW_PRODUCT", response.productCode()),
                () -> assertEquals(AccountType.CURRENT, response.accountType()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchIsScopedToTheCallersBranchAndMapsProductsFromTheCache() {
        Account account = persisted(SAVINGS.id());
        Pageable pageable = PageRequest.of(0, 20);
        when(branchAccess.restrictedBranch()).thenReturn(Optional.of("BR001"));
        when(accounts.findAll(any(Specification.class), eq(pageable))).thenReturn(new PageImpl<>(List.of(account), pageable, 1));
        when(products.catalog()).thenReturn(CATALOG);

        PageResponse<AccountSummaryResponse> page = service.search(new AccountSearchCriteria(null, null, "BR999", null), pageable);

        AccountSummaryResponse row = page.content().getFirst();
        assertAll(
                () -> assertEquals(1, page.totalElements()),
                () -> assertEquals(account.getId(), row.id()),
                () -> assertEquals("SAVINGS_STD", row.productCode()),
                () -> assertEquals(AccountType.SAVINGS, row.accountType()),
                () -> verify(branchAccess).restrictedBranch());
    }

    @Test
    void balanceSnapshotIsAppliedInASingleStatement() {
        UUID id = UUID.randomUUID();
        when(accounts.applyBalanceSnapshot(eq(id), eq(new BigDecimal("100.50")), eq(new BigDecimal("90.00")), eq(5L), eq(NOW)))
                .thenReturn(1);

        service.applyBalanceSnapshot(new ApplyBalanceSnapshotRequest(id, new BigDecimal("100.5"), new BigDecimal("90"), 5L));

        verify(accounts, never()).existsById(any(UUID.class));
    }

    @Test
    void staleBalanceSnapshotIsIgnored() {
        UUID id = UUID.randomUUID();
        when(accounts.applyBalanceSnapshot(eq(id), any(BigDecimal.class), any(BigDecimal.class), anyLong(), eq(NOW))).thenReturn(0);
        when(accounts.existsById(id)).thenReturn(true);

        assertDoesNotThrow(() -> service.applyBalanceSnapshot(
                new ApplyBalanceSnapshotRequest(id, BigDecimal.TEN, BigDecimal.TEN, 3L)));
    }

    @Test
    void balanceSnapshotForUnknownAccountIsNotFound() {
        UUID id = UUID.randomUUID();
        when(accounts.applyBalanceSnapshot(eq(id), any(BigDecimal.class), any(BigDecimal.class), anyLong(), eq(NOW))).thenReturn(0);
        when(accounts.existsById(id)).thenReturn(false);

        assertThrowsExactly(ResourceNotFoundException.class, () -> service.applyBalanceSnapshot(
                new ApplyBalanceSnapshotRequest(id, BigDecimal.TEN, BigDecimal.TEN, 3L)));
    }

    @Test
    void balanceSnapshotWithSubCentAmountIsRejectedBeforeAnyWrite() {
        UUID id = UUID.randomUUID();

        assertAll(
                () -> assertThrowsExactly(BusinessRuleException.class, () -> service.applyBalanceSnapshot(
                        new ApplyBalanceSnapshotRequest(id, new BigDecimal("1.005"), BigDecimal.ONE, 2L))),
                () -> verifyNoInteractions(accounts));
    }
}
