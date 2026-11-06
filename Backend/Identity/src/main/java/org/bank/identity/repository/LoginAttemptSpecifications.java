package org.bank.identity.repository;

import jakarta.persistence.criteria.Predicate;
import org.bank.identity.model.LoginAttempt;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LoginAttemptSpecifications {

    private LoginAttemptSpecifications() {
    }

    public static Specification<LoginAttempt> matching(LoginAttemptSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.username() != null && !criteria.username().isBlank()) {
                predicates.add(cb.equal(root.get("username"), criteria.username().trim().toLowerCase(Locale.ROOT)));
            }
            if (criteria.userId() != null) {
                predicates.add(cb.equal(root.get("userId"), criteria.userId()));
            }
            if (criteria.result() != null) {
                predicates.add(cb.equal(root.get("result"), criteria.result()));
            }
            if (criteria.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), criteria.from()));
            }
            if (criteria.to() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), criteria.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
