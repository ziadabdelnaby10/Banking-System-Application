package org.bank.account.controller;

import org.bank.common.exception.BankException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

class SafePageableTest {

    private static final Set<String> ALLOWED = Set.of("accountNo", "createdAt");
    private static final Sort DEFAULT = Sort.by(Sort.Direction.DESC, "createdAt");

    @Test
    void unsortedRequestGetsDefaultSortWithTieBreaker() {
        Pageable result = SafePageable.restrict(PageRequest.of(2, 10), ALLOWED, DEFAULT);

        assertAll(
                () -> assertEquals(2, result.getPageNumber()),
                () -> assertEquals(10, result.getPageSize()),
                () -> assertEquals(Sort.Direction.DESC, result.getSort().getOrderFor("createdAt").getDirection()),
                () -> assertNotNull(result.getSort().getOrderFor("id")));
    }

    @Test
    void requestedSortIsKeptAndStillGetsTieBreaker() {
        Pageable result = SafePageable.restrict(PageRequest.of(0, 5, Sort.by("accountNo")), ALLOWED, DEFAULT);

        assertAll(
                () -> assertNotNull(result.getSort().getOrderFor("accountNo")),
                () -> assertNotNull(result.getSort().getOrderFor("id")));
    }

    @Test
    void unknownSortPropertyIsRejected() {
        Pageable pageable = PageRequest.of(0, 5, Sort.by("customerId"));

        assertThrowsExactly(BankException.class, () -> SafePageable.restrict(pageable, ALLOWED, DEFAULT));
    }
}
