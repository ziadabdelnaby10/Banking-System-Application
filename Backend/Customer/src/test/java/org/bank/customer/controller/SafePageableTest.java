package org.bank.customer.controller;

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

    private static final Set<String> ALLOWED = Set.of("fullName", "createdAt");
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
        Pageable result = SafePageable.restrict(PageRequest.of(0, 5, Sort.by("fullName")), ALLOWED, DEFAULT);

        assertAll(
                () -> assertNotNull(result.getSort().getOrderFor("fullName")),
                () -> assertNotNull(result.getSort().getOrderFor("id")));
    }

    @Test
    void unknownSortPropertyIsRejected() {
        Pageable hostile = PageRequest.of(0, 5, Sort.by("passwordHash"));

        assertThrowsExactly(BankException.class, () -> SafePageable.restrict(hostile, ALLOWED, DEFAULT));
    }
}
