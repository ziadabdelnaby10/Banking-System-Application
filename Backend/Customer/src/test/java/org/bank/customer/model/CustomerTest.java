package org.bank.customer.model;

import org.bank.common.dto.enums.CustomerKycStatus;
import org.bank.common.dto.enums.CustomerStatus;
import org.bank.common.dto.enums.RiskLevel;
import org.bank.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

class CustomerTest {

    private static Customer newCustomer() {
        return Customer.register(100_000L, "  Mona Salem ", LocalDate.of(1990, 5, 17), "f".repeat(64), "4567", "BR001");
    }

    @Test
    void registrationFormatsCustomerNumberAndStartsActiveWithPendingKyc() {
        Customer customer = newCustomer();

        assertAll(
                () -> assertEquals("CUS0000100000", customer.getCustomerNo()),
                () -> assertEquals("Mona Salem", customer.getFullName(), "name is trimmed"),
                () -> assertEquals(CustomerStatus.ACTIVE, customer.getStatus()),
                () -> assertEquals(CustomerKycStatus.PENDING, customer.getKycStatus()),
                () -> assertEquals(RiskLevel.LOW, customer.getRiskLevel()),
                () -> assertEquals("****4567", customer.maskedNationalId()));
    }

    @ParameterizedTest
    @CsvSource({
            "ACTIVE, BLOCKED, true",
            "ACTIVE, CLOSED, true",
            "ACTIVE, ACTIVE, false",
            "BLOCKED, ACTIVE, true",
            "BLOCKED, CLOSED, true",
            "BLOCKED, BLOCKED, false",
            "CLOSED, ACTIVE, false",
            "CLOSED, BLOCKED, false",
            "CLOSED, CLOSED, false"
    })
    void statusTransitionsFollowTheLifecycle(CustomerStatus from, CustomerStatus to, boolean allowed) {
        assertEquals(allowed, from.canTransitionTo(to));
    }

    @Test
    void changeStatusReturnsPreviousStatus() {
        Customer customer = newCustomer();

        CustomerStatus previous = customer.changeStatus(CustomerStatus.BLOCKED);

        assertAll(
                () -> assertEquals(CustomerStatus.ACTIVE, previous),
                () -> assertEquals(CustomerStatus.BLOCKED, customer.getStatus()));
    }

    @Test
    void closedCustomerIsTerminalAndReadOnly() {
        Customer customer = newCustomer();
        customer.changeStatus(CustomerStatus.CLOSED);

        assertAll(
                () -> assertThrowsExactly(BusinessRuleException.class, () -> customer.changeStatus(CustomerStatus.ACTIVE)),
                () -> assertThrowsExactly(BusinessRuleException.class, () -> customer.updateProfile("New Name", null)),
                () -> assertThrowsExactly(BusinessRuleException.class, () -> customer.changeRiskLevel(RiskLevel.HIGH)),
                () -> assertEquals(CustomerStatus.CLOSED, customer.getStatus()));
    }

    @Test
    void profileUpdateOnlyAppliesProvidedFields() {
        Customer customer = newCustomer();

        customer.updateProfile(null, LocalDate.of(1991, 1, 1));

        assertAll(
                () -> assertEquals("Mona Salem", customer.getFullName()),
                () -> assertEquals(LocalDate.of(1991, 1, 1), customer.getDateOfBirth()));
    }
}
