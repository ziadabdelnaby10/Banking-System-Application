package org.bank.account.mapper;

import org.bank.common.dto.response.PageResponse;
import org.springframework.data.domain.Page;

import java.util.function.Function;

/** Converts Spring Data pages into the stable wire envelope shared by all services. */
public final class PageResponses {

    private PageResponses() {
    }

    public static <E, R> PageResponse<R> from(Page<E> page, Function<? super E, ? extends R> mapper) {
        return new PageResponse<>(
                page.getContent().stream().<R>map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
