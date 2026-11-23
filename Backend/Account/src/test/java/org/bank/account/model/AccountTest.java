package org.bank.account.model;

import org.bank.common.dto.enums.AccountStatus;
import org.bank.common.dto.enums.AccountType;
import org.bank.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

class AccountTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 11, 11, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final UUID CUSTOMER = UUID.fromString("01900000-0000-7000-8000-0000000000c1");
    private static final ProductView SAVINGS =
            new ProductView(UUID.fromString("01900000-0000-7000-8000-0000000000a1"), "SAVINGS_STD", "Standard Savings",
                    AccountType.SAVINGS, BigDecimal.ZERO, true);

    private static Account openAccount() {
        return Account.open(42L, CUSTOMER, " br001 ", SAVINGS, "EGP", NOW);
    }

    @Test
    void openCreatesActiveAccountWithZeroBalances() {
        Account account = openAccount();

        assertAll(
                () -> assertEquals("ACC000000000042", account.getAccountNo()),
                () -> assertEquals("BR001", account.getBranchCode()),
                () -> assertEquals(CUSTOMER, account.getCustomerId()),
                () -> assertEquals(SAVINGS.id(), account.getProductId()),
                () -> assertEquals("EGP", account.getCurrency()),
                () -> assertEquals(AccountStatus.ACTIVE, account.getStatus()),
                () -> assertEquals(new BigDecimal("0.00"), account.getLedgerBalance()),
                () -> assertEquals(new BigDecimal("0.00"), account.getAvailableBalance()),
                () -> assertEquals(0L, account.getBalanceVersion()),
                () -> assertEquals(NOW, account.getOpenedAt()),
                () -> assertEquals(NOW, account.getBalanceAsOf()),
                () -> assertNull(account.getClosedAt()));
    }

    @Test
    void openRejectsRetiredProduct() {
        ProductView retired = new ProductView(SAVINGS.id(), "OLD", "Old", AccountType.SAVINGS, BigDecimal.ZERO, false);

        assertThrowsExactly(BusinessRuleException.class, () -> Account.open(1L, CUSTOMER, "BR001", retired, "EGP", NOW));
    }

    @Test
    void freezeThenUnfreezeReturnsPreviousStatus() {
        Account account = openAccount();

        AccountStatus beforeFreeze = account.freeze();
        AccountStatus beforeUnfreeze = account.unfreeze();

        assertAll(
                () -> assertEquals(AccountStatus.ACTIVE, beforeFreeze),
                () -> assertEquals(AccountStatus.FROZEN, beforeUnfreeze),
                () -> assertEquals(AccountStatus.ACTIVE, account.getStatus()));
    }

    @Test
    void frozenAccountCannotBeFrozenAgain() {
        Account account = openAccount();
        account.freeze();

        assertThrowsExactly(BusinessRuleException.class, account::freeze);
    }

    @Test
    void activeAccountCannotBeUnfrozen() {
        assertThrowsExactly(BusinessRuleException.class, () -> openAccount().unfreeze());
    }

    @Test
    void closeSetsClosedAtAndIsTerminal() {
        Account account = openAccount();
        OffsetDateTime closeTime = NOW.plusDays(1);

        AccountStatus previous = account.close(closeTime);

        assertAll(
                () -> assertEquals(AccountStatus.ACTIVE, previous),
                () -> assertEquals(AccountStatus.CLOSED, account.getStatus()),
                () -> assertEquals(closeTime, account.getClosedAt()),
                () -> assertThrowsExactly(BusinessRuleException.class, () -> account.close(closeTime)),
                () -> assertThrowsExactly(BusinessRuleException.class, account::freeze),
                () -> assertThrowsExactly(BusinessRuleException.class, account::unfreeze));
    }

    @Test
    void frozenAccountCanBeClosed() {
        Account account = openAccount();
        account.freeze();

        assertEquals(AccountStatus.FROZEN, account.close(NOW));
    }

    @Test
    void accountWithFundsCannotBeClosed() {
        Account account = openAccount();
        ReflectionTestUtils.setField(account, "ledgerBalance", new BigDecimal("10.00"));

        assertAll(
                () -> assertThrowsExactly(BusinessRuleException.class, () -> account.close(NOW)),
                () -> assertEquals(AccountStatus.ACTIVE, account.getStatus()),
                () -> assertNull(account.getClosedAt()));
    }

    @Test
    void accountWithOnlyAvailableFundsCannotBeClosed() {
        Account account = openAccount();
        ReflectionTestUtils.setField(account, "availableBalance", new BigDecimal("0.01"));

        assertThrowsExactly(BusinessRuleException.class, () -> account.close(NOW));
    }

    @Test
    void moneyIsNormalisedToTwoDecimalsAndNeverRounded() {
        assertAll(
                () -> assertEquals(new BigDecimal("5.00"), Account.toMoney(new BigDecimal("5"))),
                () -> assertEquals(new BigDecimal("5.10"), Account.toMoney(new BigDecimal("5.1"))),
                () -> assertEquals(new BigDecimal("5.00"), Account.toMoney(new BigDecimal("5.000"))),
                () -> assertThrowsExactly(BusinessRuleException.class, () -> Account.toMoney(new BigDecimal("5.001"))));
    }

    @Test
    void idIsAssignedOnPersistOnly() {
        Account account = openAccount();
        assertNull(account.getId());

        ReflectionTestUtils.invokeMethod(account, "onCreate");

        assertNotNull(account.getId());
    }
}
