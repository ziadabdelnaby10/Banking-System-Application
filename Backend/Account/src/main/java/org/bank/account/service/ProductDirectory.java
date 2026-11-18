package org.bank.account.service;

import lombok.RequiredArgsConstructor;
import org.bank.account.model.ProductView;
import org.bank.account.repository.AccountProductRepository;
import org.bank.common.exception.ResourceNotFoundException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The product catalogue is tiny, read on every account operation and practically static, so the whole catalogue is
 * kept in memory as one immutable snapshot (see the {@code account-products} cache in the service configuration).
 * A separate bean, so the cache proxy is not bypassed by self-invocation. Only immutable views are cached, never entities.
 */
@Component
@RequiredArgsConstructor
public class ProductDirectory {

    static final String CACHE = "account-products";

    private final AccountProductRepository products;

    /** All products, indexed by id and by code. */
    public record Catalog(List<ProductView> all, Map<UUID, ProductView> byId, Map<String, ProductView> byCode) {

        static Catalog of(List<ProductView> views) {
            Map<UUID, ProductView> byId = new LinkedHashMap<>();
            Map<String, ProductView> byCode = new LinkedHashMap<>();
            views.forEach(view -> {
                byId.put(view.id(), view);
                byCode.put(view.code(), view);
            });
            return new Catalog(List.copyOf(views), Collections.unmodifiableMap(byId), Collections.unmodifiableMap(byCode));
        }

        public Optional<ProductView> find(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        public Optional<ProductView> findByCode(String code) {
            return Optional.ofNullable(byCode.get(code));
        }
    }

    @Cacheable(cacheNames = CACHE, key = "'all'")
    @Transactional(readOnly = true)
    public Catalog catalog() {
        return Catalog.of(products.findAll(Sort.by("code")).stream().map(ProductView::from).toList());
    }

    /** Uncached lookup, for a product created after the cached snapshot was taken. */
    @Transactional(readOnly = true)
    public ProductView loadFresh(UUID productId) {
        return products.findById(productId).map(ProductView::from)
                .orElseThrow(() -> new ResourceNotFoundException("Account product", productId));
    }
}
