package org.bank.account.repository;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountSpecificationsTest {

    @Test
    void likeWildcardsAreEscaped() {
        assertEquals("ACC\\%\\_\\\\", AccountSpecifications.escapeLike("ACC%_\\"));
    }

    @Test
    void plainValuesAreUnchanged() {
        assertEquals("ACC0001", AccountSpecifications.escapeLike("ACC0001"));
    }
}
