package org.bank.customer.service;

import lombok.RequiredArgsConstructor;
import org.bank.customer.model.Branch;
import org.bank.customer.repository.BranchRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Read-mostly branch lookups used on every customer registration. Results are cached in memory (see the
 * {@code branches} cache in application.yml) and evicted after a branch changes. A separate bean, so the
 * cache proxy is not bypassed by self-invocation. Only an immutable view is cached, never the entity.
 */
@Component
@RequiredArgsConstructor
public class BranchDirectory {

    static final String CACHE = "branches";

    private final BranchRepository branches;

    /** Immutable snapshot of a branch. */
    public record BranchView(String code, String name, boolean active) {
    }

    @Cacheable(cacheNames = CACHE, key = "#code", unless = "#result == null")
    @Transactional(readOnly = true)
    public Optional<BranchView> find(String code) {
        return branches.findByCode(code).map(BranchDirectory::view);
    }

    @CacheEvict(cacheNames = CACHE, key = "#code")
    public void evict(String code) {
        // eviction is done by the annotation
    }

    private static BranchView view(Branch branch) {
        return new BranchView(branch.getCode(), branch.getName(), branch.isActive());
    }
}
