package org.bank.account.model;

import org.bank.common.dto.enums.AccountStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountStatusTransitionTest {

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @CsvSource({
            "PENDING, ACTIVE, true",
            "PENDING, CLOSED, true",
            "PENDING, FROZEN, false",
            "PENDING, PENDING, false",
            "ACTIVE, FROZEN, true",
            "ACTIVE, CLOSED, true",
            "ACTIVE, PENDING, false",
            "ACTIVE, ACTIVE, false",
            "FROZEN, ACTIVE, true",
            "FROZEN, CLOSED, true",
            "FROZEN, PENDING, false",
            "FROZEN, FROZEN, false",
            "CLOSED, ACTIVE, false",
            "CLOSED, FROZEN, false",
            "CLOSED, PENDING, false",
            "CLOSED, CLOSED, false"
    })
    void transitionMatrix(AccountStatus from, AccountStatus to, boolean allowed) {
        assertEquals(allowed, from.canTransitionTo(to));
    }
}
