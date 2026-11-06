package org.bank.identity.repository;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Subquery;
import org.bank.identity.model.User;
import org.bank.identity.model.UserRole;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Composable filters for user search. All values are bound as parameters (no string concatenation). */
public final class UserSpecifications {

    private static final char ESCAPE = '\\';

    private UserSpecifications() {
    }

    /**
     * @param restrictedBranch branch forced by branch scoping; overrides the client-supplied branch filter
     */
    public static Specification<User> matching(UserSearchCriteria criteria, Optional<String> restrictedBranch) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (criteria.q() != null && !criteria.q().isBlank()) {
                String prefix = escapeLike(criteria.q().trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), prefix, ESCAPE),
                        cb.like(cb.lower(root.get("email")), prefix, ESCAPE)));
            }
            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.status()));
            }
            String branch = restrictedBranch.orElse(criteria.branchCode());
            if (branch != null && !branch.isBlank()) {
                predicates.add(cb.equal(root.get("branchCode"), branch));
            }
            if (criteria.role() != null && !criteria.role().isBlank()) {
                // EXISTS keeps one row per user (a join would duplicate users and break paging counts).
                Subquery<Integer> exists = query.subquery(Integer.class);
                var userRole = exists.from(UserRole.class);
                Join<Object, Object> role = userRole.join("role", JoinType.INNER);
                exists.select(cb.literal(1)).where(
                        cb.equal(userRole.get("user"), root),
                        cb.equal(cb.upper(role.get("name")), criteria.role().trim().toUpperCase(Locale.ROOT)));
                predicates.add(cb.exists(exists));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
