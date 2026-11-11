package org.bank.customer.service.implementation;

import lombok.RequiredArgsConstructor;
import org.bank.common.dto.enums.CustomerKycStatus;
import org.bank.common.exception.ResourceNotFoundException;
import org.bank.common.security.principal.CurrentUser;
import org.bank.common.dto.enums.KycDecision;
import org.bank.common.dto.response.KycRecordResponse;
import org.bank.common.dto.request.ReviewKycRequest;
import org.bank.common.dto.request.SubmitKycRequest;
import org.bank.customer.event.CustomerEvents;
import org.bank.customer.mapper.KycMapper;
import org.bank.customer.model.Customer;
import org.bank.customer.model.KycRecord;
import org.bank.customer.model.KycStatusDeriver;
import org.bank.customer.repository.KycRecordRepository;
import org.bank.customer.service.CustomerLookup;
import org.bank.customer.service.KycService;
import org.bank.customer.service.OutboxRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KycServiceImpl implements KycService {

    private static final Logger log = LoggerFactory.getLogger(KycServiceImpl.class);

    private final KycRecordRepository kycRecords;
    private final KycMapper kycMapper;
    private final CustomerLookup customerLookup;
    private final OutboxRecorder outbox;
    private final Clock clock;

    @Override
    @Transactional
    public KycRecordResponse submit(UUID customerId, SubmitKycRequest request) {
        Customer customer = customerLookup.requireVisible(customerId);
        customer.requireNotClosed("given new KYC documents");

        UUID submitter = CurrentUser.require().userId();
        // A second open submission for the same document type violates the partial unique index (-> 409).
        KycRecord record = kycRecords.save(KycRecord.submit(customerId, request.documentType(),
                request.documentRef(), submitter, OffsetDateTime.now(clock)));
        refreshCustomerKycStatus(customer);

        outbox.record(CustomerEvents.CUSTOMER_AGGREGATE, customerId, CustomerEvents.KYC_SUBMITTED,
                new CustomerEvents.KycSubmitted(customerId, record.getId(), record.getDocumentType().name(), submitter));
        log.info("AUDIT kyc-submitted actor={} customer={} record={} type={}",
                submitter, customerId, record.getId(), record.getDocumentType());
        return kycMapper.toResponse(record);
    }

    @Override
    public List<KycRecordResponse> list(UUID customerId) {
        customerLookup.requireVisible(customerId);
        return kycRecords.findByCustomerIdOrderBySubmittedAtDesc(customerId).stream()
                .map(kycMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public KycRecordResponse review(UUID customerId, UUID kycRecordId, ReviewKycRequest request) {
        Customer customer = customerLookup.requireVisible(customerId);
        KycRecord record = kycRecords.findByIdAndCustomerId(kycRecordId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("KYC record", kycRecordId));

        UUID reviewer = CurrentUser.require().userId();
        OffsetDateTime now = OffsetDateTime.now(clock);
        // The record enforces maker-checker (reviewer != submitter) and immutability of decided records.
        if (request.decision() == KycDecision.APPROVED) {
            record.approve(reviewer, request.notes(), now);
        } else {
            record.reject(reviewer, request.notes(), now);
        }
        CustomerKycStatus customerStatus = refreshCustomerKycStatus(customer);

        outbox.record(CustomerEvents.CUSTOMER_AGGREGATE, customerId, CustomerEvents.KYC_REVIEWED,
                new CustomerEvents.KycReviewed(customerId, kycRecordId, record.getDocumentType().name(),
                        request.decision().name(), customerStatus.name(), reviewer));
        log.info("AUDIT kyc-reviewed actor={} customer={} record={} decision={} customerKyc={}",
                reviewer, customerId, kycRecordId, request.decision(), customerStatus);
        return kycMapper.toResponse(record);
    }

    /** Recomputes the customer's KYC standing from two-column rows (one query); flushes pending changes first. */
    private CustomerKycStatus refreshCustomerKycStatus(Customer customer) {
        CustomerKycStatus derived = KycStatusDeriver.derive(kycRecords.findOutcomesByCustomerId(customer.getId()));
        customer.applyKycStatus(derived);
        return derived;
    }
}
