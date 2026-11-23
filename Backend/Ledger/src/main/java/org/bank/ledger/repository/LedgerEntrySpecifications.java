package org.bank.ledger.repository;

import jakarta.persistence.criteria.Predicate;
import org.bank.ledger.model.LedgerEntry;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Composable statement filters. All values are bound as parameters (no string concatenation). */
public final class LedgerEntrySpecifications {

    private LedgerEntrySpecifications() {
    }

    public static Specification<LedgerEntry> matching(UUID accountId, LedgerEntrySearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("accountId"), accountId));
            if (criteria.entryType() != null) {
                predicates.add(cb.equal(root.get("entryType"), criteria.entryType()));
            }
            if (criteria.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<OffsetDateTime>get("postedAt"), criteria.from()));
            }
            if (criteria.to() != null) {
                predicates.add(cb.lessThan(root.<OffsetDateTime>get("postedAt"), criteria.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
