package org.bank.account.controller;

import org.bank.common.error.ErrorCode;
import org.bank.common.exception.BankException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Client-supplied sort properties end up in the query, so only an explicit whitelist is accepted.
 * A unique tie-breaker ({@code id}) is always appended: without it rows with equal sort values can
 * appear on two pages or on none while the user pages through a list.
 */
final class SafePageable {

    private static final String TIE_BREAKER = "id";

    private SafePageable() {
    }

    static Pageable restrict(Pageable pageable, Set<String> allowedSortProperties, Sort defaultSort) {
        Sort requested = pageable.getSort();
        if (requested.isUnsorted()) {
            requested = defaultSort;
        } else {
            for (Sort.Order order : requested) {
                if (!allowedSortProperties.contains(order.getProperty())) {
                    throw new BankException(ErrorCode.BAD_REQUEST, "Unsupported sort property: " + order.getProperty());
                }
            }
        }
        Sort stable = requested.getOrderFor(TIE_BREAKER) == null ? requested.and(Sort.by(TIE_BREAKER)) : requested;
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), stable);
    }
}
