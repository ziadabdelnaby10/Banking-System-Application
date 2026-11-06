package org.bank.identity.controller;

import org.bank.common.error.ErrorCode;
import org.bank.common.exception.BankException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Client-supplied sort properties end up in the query, so only an explicit whitelist is accepted.
 * Unknown properties are rejected (400) instead of surfacing as a persistence error.
 */
final class SafePageable {

    private SafePageable() {
    }

    static Pageable restrict(Pageable pageable, Set<String> allowedSortProperties, Sort defaultSort) {
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
        }
        for (Sort.Order order : pageable.getSort()) {
            if (!allowedSortProperties.contains(order.getProperty())) {
                throw new BankException(ErrorCode.BAD_REQUEST, "Unsupported sort property: " + order.getProperty());
            }
        }
        return pageable;
    }
}
