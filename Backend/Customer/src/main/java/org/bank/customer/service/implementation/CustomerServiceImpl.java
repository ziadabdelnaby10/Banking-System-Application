package org.bank.customer.service.implementation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bank.common.dto.enums.CustomerStatus;
import org.bank.common.dto.response.PageResponse;
import org.bank.common.exception.BusinessRuleException;
import org.bank.common.exception.DuplicateResourceException;
import org.bank.common.security.access.BranchAccessEvaluator;
import org.bank.common.security.correlation.CorrelationIds;
import org.bank.common.security.principal.AuthenticatedUser;
import org.bank.common.security.principal.CurrentUser;
import org.bank.common.dto.request.ChangeCustomerStatusRequest;
import org.bank.common.dto.response.CustomerResponse;
import org.bank.common.dto.response.CustomerSummaryResponse;
import org.bank.common.dto.response.KycRecordResponse;
import org.bank.common.dto.request.RegisterCustomerRequest;
import org.bank.common.dto.request.UpdateCustomerRequest;
import org.bank.customer.event.CustomerEvents;
import org.bank.customer.mapper.CustomerMapper;
import org.bank.customer.mapper.KycMapper;
import org.bank.customer.mapper.PageResponses;
import org.bank.customer.model.Branch;
import org.bank.customer.model.Customer;
import org.bank.customer.model.CustomerStatusHistory;
import org.bank.customer.repository.CustomerRepository;
import org.bank.customer.repository.CustomerSearchCriteria;
import org.bank.customer.repository.CustomerSpecifications;
import org.bank.customer.repository.CustomerStatusHistoryRepository;
import org.bank.customer.repository.KycRecordRepository;
import org.bank.customer.service.BranchDirectory;
import org.bank.customer.service.CustomerLookup;
import org.bank.customer.service.CustomerService;
import org.bank.customer.service.NationalIdFingerprint;
import org.bank.customer.service.OutboxRecorder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Query budget (no N+1 by construction, the entities have no associations):
 * <ul>
 *   <li>search: 1 query for the page + 1 count (skipped on a short first page);</li>
 *   <li>get / update / status change: customer + KYC records = 2 queries (branch name comes from the cache);</li>
 *   <li>register: sequence + duplicate check + inserts.</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerServiceImpl implements CustomerService {

    /** Changing the risk classification is a compliance decision, so it needs the same authority as a status change. */
    private static final String RISK_CHANGE_PERMISSION = "CUSTOMER_STATUS_CHANGE";

    private final CustomerRepository customerRepository;
    private final KycRecordRepository kycRecordRepository;
    private final CustomerStatusHistoryRepository customerStatusHistoryRepository;
    private final CustomerMapper customerMapper;
    private final KycMapper kycMapper;
    private final CustomerLookup customerLookup;
    private final BranchDirectory branchDirectory;
    private final BranchAccessEvaluator branchAccess;
    private final NationalIdFingerprint nationalIds;
    private final OutboxRecorder outbox;
    private final Clock clock;

    @Override
    @Transactional
    public CustomerResponse register(RegisterCustomerRequest request) {
        String branchCode = Branch.normalizeCode(request.branchCode());
        BranchDirectory.BranchView branch = branchDirectory.find(branchCode)
                .filter(BranchDirectory.BranchView::active)
                .orElseThrow(() -> new BusinessRuleException("Branch " + branchCode + " does not exist or is not active"));

        String nationalId = NationalIdFingerprint.normalize(request.nationalId());
        String fingerprint = nationalIds.fingerprint(nationalId);
        // Friendly early answer; the unique constraint remains the real guard against races (-> 409).
        if (customerRepository.existsByNationalIdFingerprint(fingerprint)) {
            throw new DuplicateResourceException("A customer with this national id is already registered");
        }

        Customer customer = customerRepository.save(Customer.register(customerRepository.nextCustomerSequence(), request.fullName(),
                request.dateOfBirth(), fingerprint, NationalIdFingerprint.lastCharacters(nationalId), branchCode));

        UUID actor = CurrentUser.require().userId();
        outbox.record(CustomerEvents.CUSTOMER_AGGREGATE, customer.getId(), CustomerEvents.CUSTOMER_REGISTERED,
                new CustomerEvents.CustomerRegistered(customer.getId(), customer.getCustomerNo(), branchCode, actor));
        log.info("AUDIT customer-registered actor={} customer={} branch={}", actor, customer.getId(), branchCode);
        return customerMapper.toResponse(customer, branch.name(), List.of());
    }

    @Override
    public CustomerResponse get(UUID customerId) {
        return detail(customerLookup.requireVisible(customerId));
    }

    @Override
    public PageResponse<CustomerSummaryResponse> search(CustomerSearchCriteria criteria, Pageable pageable) {
        Page<Customer> page = customerRepository.findAll(
                CustomerSpecifications.matching(criteria, branchAccess.restrictedBranch()), pageable);
        return PageResponses.from(page, customerMapper::toSummary);
    }

    @Override
    @Transactional
    public CustomerResponse update(UUID customerId, UpdateCustomerRequest request) {
        Customer customer = customerLookup.requireVisible(customerId);

        if (request.riskLevel() != null && !CurrentUser.require().hasPermission(RISK_CHANGE_PERMISSION)) {
            throw new AccessDeniedException("Changing the risk level requires the " + RISK_CHANGE_PERMISSION + " permission");
        }
        customer.updateProfile(request.fullName(), request.dateOfBirth());
        if (request.riskLevel() != null) {
            customer.changeRiskLevel(request.riskLevel());
        }
        log.info("AUDIT customer-updated actor={} customer={}", CurrentUser.require().userId(), customerId);
        return detail(customer);
    }

    @Override
    @Transactional
    public CustomerResponse changeStatus(UUID customerId, ChangeCustomerStatusRequest request) {
        Customer customer = customerLookup.requireVisible(customerId);
        AuthenticatedUser actor = CurrentUser.require();

        // Seam for ADR-005: closing a customer is a high-risk action; routing it through the Approval
        // service (maker-checker) plugs in here once that service exists.
        CustomerStatus previous = customer.changeStatus(request.status());
        customerStatusHistoryRepository.save(CustomerStatusHistory.record(customerId, previous, request.status(), request.reason(),
                actor.userId(), OffsetDateTime.now(clock), CorrelationIds.current()));
        outbox.record(CustomerEvents.CUSTOMER_AGGREGATE, customerId, CustomerEvents.CUSTOMER_STATUS_CHANGED,
                new CustomerEvents.CustomerStatusChanged(customerId, customer.getCustomerNo(), customer.getBranchCode(),
                        previous.name(), request.status().name(), request.reason().trim(), actor.userId()));

        log.info("AUDIT customer-status-changed actor={} customer={} from={} to={}",
                actor.userId(), customerId, previous, request.status());
        return detail(customer);
    }

    /** Customer + its KYC records in exactly one extra query; the branch name is served from the cache. */
    private CustomerResponse detail(Customer customer) {
        List<KycRecordResponse> records = kycRecordRepository.findByCustomerIdOrderBySubmittedAtDesc(customer.getId()).stream()
                .map(kycMapper::toResponse)
                .toList();
        String branchName = branchDirectory.find(customer.getBranchCode())
                .map(BranchDirectory.BranchView::name)
                .orElse(customer.getBranchCode());
        return customerMapper.toResponse(customer, branchName, records);
    }
}
