package org.bank.customer.service;

import org.bank.common.dto.response.KycRecordResponse;
import org.bank.common.dto.request.ReviewKycRequest;
import org.bank.common.dto.request.SubmitKycRequest;

import java.util.List;
import java.util.UUID;

public interface KycService {

    KycRecordResponse submit(UUID customerId, SubmitKycRequest request);

    List<KycRecordResponse> list(UUID customerId);

    /** Checker step: approve or reject evidence submitted by someone else. Decisions are final. */
    KycRecordResponse review(UUID customerId, UUID kycRecordId, ReviewKycRequest request);
}
