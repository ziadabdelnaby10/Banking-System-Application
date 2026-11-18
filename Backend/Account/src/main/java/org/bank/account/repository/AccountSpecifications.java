package org.bank.account.repository;

import jakarta.persistence.criteria.Predicate;
import org.bank.account.model.Account;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Composable account filters. All values are bound as parameters (no string concatenation). */
public final class AccountSpecifications {

    private static final char ESCAPE = '\\';

    private AccountSpecifications() {
    }

    /**
     * @param restrictedBranch branch forced by branch scoping; overrides the client-supplied branch filter
     */
    public static Specification<Account> matching(AccountSearchCriteria criteria, Optional<String> restrictedBranch) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.customerId() != null) {
                predicates.add(cb.equal(root.get("customerId"), criteria.customerId()));
            }
            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.status()));
            }
            if (criteria.accountNo() != null && !criteria.accountNo().isBlank()) {
                // Prefix match only, so the account_no pattern index can be used.
                String prefix = escapeLike(criteria.accountNo().trim().toUpperCase(Locale.ROOT));
                predicates.add(cb.like(root.get("accountNo"), prefix + "%", ESCAPE));
            }
            String branch = restrictedBranch.orElse(criteria.branchCode());
            if (branch != null && !branch.isBlank()) {
                predicates.add(cb.equal(root.get("branchCode"), Account.normalizeBranchCode(branch)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
