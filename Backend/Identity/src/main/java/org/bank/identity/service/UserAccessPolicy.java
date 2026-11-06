package org.bank.identity.service;

import lombok.RequiredArgsConstructor;
import org.bank.common.exception.BusinessRuleException;
import org.bank.common.exception.ResourceNotFoundException;
import org.bank.common.security.access.BranchAccessEvaluator;
import org.bank.common.security.principal.AuthenticatedUser;
import org.bank.common.security.principal.CurrentUser;
import org.bank.identity.model.Role;
import org.bank.identity.model.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Authorisation rules that depend on <em>who the target is</em> and therefore cannot be expressed with
 * a static {@code @PreAuthorize}: branch visibility, no self-service privilege changes, and who may
 * hand out the ADMIN role. Shared by the user and admin services.
 * <p>
 * Seam for ADR-005: privileged changes (role grants, locks) currently require the function permission
 * only; routing them through the Approval service (maker-checker) plugs in here once it exists.
 */
@Component
@RequiredArgsConstructor
public class UserAccessPolicy {

    static final String ADMIN_ROLE = "ADMIN";

    private final BranchAccessEvaluator branchAccess;

    /**
     * Users outside the caller's branch are reported as not found rather than forbidden, so their
     * existence cannot be probed across branches. A caller can always see themselves.
     */
    public void requireVisible(User user) {
        AuthenticatedUser caller = CurrentUser.require();
        if (caller.userId().equals(user.getId())) {
            return;
        }
        if (!branchAccess.canAccess(user.getBranchCode())) {
            throw new ResourceNotFoundException("User", user.getId());
        }
    }

    public void requireNotSelf(UUID targetUserId, String action) {
        if (CurrentUser.require().userId().equals(targetUserId)) {
            throw new BusinessRuleException("You cannot " + action);
        }
    }

    public void requireCanManageRole(Role role) {
        if (ADMIN_ROLE.equalsIgnoreCase(role.getName()) && !CurrentUser.require().hasRole(ADMIN_ROLE)) {
            throw new AccessDeniedException("Only administrators may grant or revoke the ADMIN role");
        }
    }
}
