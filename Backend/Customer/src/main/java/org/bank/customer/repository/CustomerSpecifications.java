package org.bank.customer.repository;

import jakarta.persistence.criteria.Predicate;
import org.bank.customer.model.Branch;
import org.bank.customer.model.Customer;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Composable customer filters. All values are bound as parameters (no string concatenation). */
public final class CustomerSpecifications {

    private static final char ESCAPE = '\\';

    private CustomerSpecifications() {
    }

    /**
     * @param restrictedBranch branch forced by branch scoping; overrides the client-supplied branch filter
     */
    public static Specification<Customer> matching(CustomerSearchCriteria criteria, Optional<String> restrictedBranch) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.q() != null && !criteria.q().isBlank()) {
                // Prefix matches only, so the lower(full_name) / customer_no pattern indexes can be used.
                String term = escapeLike(criteria.q().trim().toLowerCase(Locale.ROOT));
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), term + "%", ESCAPE),
                        cb.like(root.get("customerNo"), term.toUpperCase(Locale.ROOT) + "%", ESCAPE)));
            }
            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.status()));
            }
            if (criteria.kycStatus() != null) {
                predicates.add(cb.equal(root.get("kycStatus"), criteria.kycStatus()));
            }
            if (criteria.riskLevel() != null) {
                predicates.add(cb.equal(root.get("riskLevel"), criteria.riskLevel()));
            }
            String branch = restrictedBranch.orElse(criteria.branchCode());
            if (branch != null && !branch.isBlank()) {
                predicates.add(cb.equal(root.get("branchCode"), Branch.normalizeCode(branch)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
