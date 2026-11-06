package org.bank.common.security.access;

import org.bank.common.security.config.BankSecurityProperties;
import org.bank.common.security.principal.AuthenticatedUser;
import org.bank.common.security.principal.CurrentUser;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.Set;

/**
 * Branch scoping rules. Registered as bean {@code branchAccess}, e.g.
 * {@code @PreAuthorize("@branchAccess.canAccess(#branchCode)")}.
 * Users holding a bypass role (default ADMIN) see every branch; everyone else is limited to their own.
 */
public class BranchAccessEvaluator {

    private final Set<String> bypassRoles;

    public BranchAccessEvaluator(BankSecurityProperties properties) {
        this.bypassRoles = Set.copyOf(properties.branchBypassRoles());
    }

    public boolean canAccess(String targetBranch) {
        AuthenticatedUser user = CurrentUser.get().orElse(null);
        if (user == null) {
            return false;
        }
        if (isUnrestricted(user)) {
            return true;
        }
        return user.branchCode() != null && user.branchCode().equalsIgnoreCase(targetBranch);
    }

    /**
     * Branch a query must be filtered by, or empty when the caller may see all branches.
     *
     * @throws AccessDeniedException if the caller is branch-restricted but has no branch assigned
     */
    public Optional<String> restrictedBranch() {
        AuthenticatedUser user = CurrentUser.require();
        if (isUnrestricted(user)) {
            return Optional.empty();
        }
        if (user.branchCode() == null) {
            throw new AccessDeniedException("Caller has no branch assignment");
        }
        return Optional.of(user.branchCode());
    }

    private boolean isUnrestricted(AuthenticatedUser user) {
        return user.roles().stream().anyMatch(bypassRoles::contains);
    }
}
