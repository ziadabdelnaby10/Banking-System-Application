package org.bank.common.dto.response;

import java.io.Serializable;
import java.util.List;
import java.util.function.Function;

/** Stable page envelope so the wire format does not depend on Spring Data's Page internals. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages)
        implements Serializable {

    public <R> PageResponse<R> map(Function<? super T, ? extends R> mapper) {
        return new PageResponse<>(content.stream().<R>map(mapper).toList(), page, size, totalElements, totalPages);
    }
}
