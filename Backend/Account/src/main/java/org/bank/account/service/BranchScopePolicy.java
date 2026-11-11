package org.bank.account.service;

import lombok.RequiredArgsConstructor;
import org.bank.common.exception.ResourceNotFoundException;
import org.bank.common.security.access.BranchAccessEvaluator;
import org.springframework.stereotype.Component;

/**
 * Branch scoping for single-resource access. Resources outside the caller's branch are reported as
 * "not found" rather than "forbidden" so their existence cannot be probed across branches.
 * (List queries are scoped separately, in the query itself, via {@code BranchAccessEvaluator#restrictedBranch}.)
 */
@Component
@RequiredArgsConstructor
public class BranchScopePolicy {

    private final BranchAccessEvaluator branchAccess;

    public void requireVisible(String resourceName, Object identifier, String branchCode) {
        if (!branchAccess.canAccess(branchCode)) {
            throw new ResourceNotFoundException(resourceName, identifier);
        }
    }
}
