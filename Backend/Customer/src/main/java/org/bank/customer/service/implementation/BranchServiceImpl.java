package org.bank.customer.service.implementation;

import lombok.RequiredArgsConstructor;
import org.bank.common.exception.DuplicateResourceException;
import org.bank.common.exception.ResourceNotFoundException;
import org.bank.common.security.access.BranchAccessEvaluator;
import org.bank.common.security.principal.CurrentUser;
import org.bank.common.dto.response.BranchResponse;
import org.bank.common.dto.request.CreateBranchRequest;
import org.bank.common.dto.request.UpdateBranchRequest;
import org.bank.customer.event.CustomerEvents;
import org.bank.customer.mapper.BranchMapper;
import org.bank.customer.model.Branch;
import org.bank.customer.repository.BranchRepository;
import org.bank.customer.service.BranchDirectory;
import org.bank.customer.service.BranchScopePolicy;
import org.bank.customer.service.BranchService;
import org.bank.customer.service.OutboxRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BranchServiceImpl implements BranchService {

    private static final Logger log = LoggerFactory.getLogger(BranchServiceImpl.class);

    private final BranchRepository branches;
    private final BranchMapper branchMapper;
    private final BranchScopePolicy scopePolicy;
    private final BranchAccessEvaluator branchAccess;
    private final BranchDirectory branchDirectory;
    private final OutboxRecorder outbox;

    @Override
    @Transactional
    public BranchResponse create(CreateBranchRequest request) {
        log.info("Creating new branch {}", request);
        String code = Branch.normalizeCode(request.code());
        if (branches.existsByCode(code)) {
            throw new DuplicateResourceException("Branch code is already in use");
        }
        Branch branch = branches.save(Branch.open(code, request.name(), request.region()));
        outbox.record(CustomerEvents.BRANCH_AGGREGATE, branch.getId(), CustomerEvents.BRANCH_CREATED,
                new CustomerEvents.BranchChanged(branch.getCode(), branch.getName(), branch.getStatus().name()));
        evictAfterCommit(code);
        log.info("AUDIT branch-created actor={} branch={}", actor(), code);
        return branchMapper.toResponse(branch);
    }

    @Override
    public BranchResponse get(String code) {
        return branchMapper.toResponse(requireVisible(code));
    }

    @Override
    public List<BranchResponse> list() {
        log.info("Listing all branches");
        // Restricted callers get their own branch through an indexed lookup, never a full-table scan.
        List<Branch> visible = branchAccess.restrictedBranch()
                .map(own -> branches.findByCode(own).map(List::of).orElse(List.of()))
                .orElseGet(branches::findAllByOrderByCodeAsc);
        return visible.stream().map(branchMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public BranchResponse update(String code, UpdateBranchRequest request) {
        log.info("Updating branch {}", request);
        Branch branch = requireVisible(code);
        branch.update(request.name(), request.region());
        if (request.status() != null) {
            branch.changeStatus(request.status());
        }
        outbox.record(CustomerEvents.BRANCH_AGGREGATE, branch.getId(), CustomerEvents.BRANCH_UPDATED,
                new CustomerEvents.BranchChanged(branch.getCode(), branch.getName(), branch.getStatus().name()));
        evictAfterCommit(branch.getCode());
        log.info("AUDIT branch-updated actor={} branch={} status={}", actor(), branch.getCode(), branch.getStatus());
        return branchMapper.toResponse(branch);
    }

    private Branch requireVisible(String code) {
        String normalized = Branch.normalizeCode(code);
        Branch branch = branches.findByCode(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", normalized));
        scopePolicy.requireVisible("Branch", normalized, branch.getCode());
        return branch;
    }

    /** Evicting before commit could let a concurrent reader re-cache the old value, so wait for the commit. */
    private void evictAfterCommit(String code) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                branchDirectory.evict(code);
            }
        });
    }

    private static String actor() {
        return CurrentUser.get().map(c -> c.userId().toString()).orElse("system");
    }
}
