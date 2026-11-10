package org.bank.customer.model;

import org.bank.common.dto.enums.CustomerKycStatus;
import org.bank.common.dto.enums.KycDocumentType;
import org.bank.common.dto.enums.KycRecordStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KycStatusDeriverTest {

    private static KycOutcome outcome(KycDocumentType type, KycRecordStatus status) {
        return new KycOutcome(type, status);
    }

    @Test
    void noRecordsMeansPending() {
        assertEquals(CustomerKycStatus.PENDING, KycStatusDeriver.derive(List.of()));
    }

    @Test
    void approvedIdentityDocumentVerifiesTheCustomer() {
        assertEquals(CustomerKycStatus.VERIFIED, KycStatusDeriver.derive(List.of(
                outcome(KycDocumentType.UTILITY_BILL, KycRecordStatus.PENDING),
                outcome(KycDocumentType.PASSPORT, KycRecordStatus.APPROVED))));
    }

    @Test
    void approvedSupportingDocumentAloneDoesNotVerify() {
        assertEquals(CustomerKycStatus.PENDING, KycStatusDeriver.derive(List.of(
                outcome(KycDocumentType.UTILITY_BILL, KycRecordStatus.APPROVED))));
    }

    @Test
    void anythingAwaitingReviewKeepsTheCustomerPending() {
        assertEquals(CustomerKycStatus.PENDING, KycStatusDeriver.derive(List.of(
                outcome(KycDocumentType.NATIONAL_ID, KycRecordStatus.REJECTED),
                outcome(KycDocumentType.PASSPORT, KycRecordStatus.PENDING))));
    }

    @Test
    void onlyRejectionsMeanRejected() {
        assertEquals(CustomerKycStatus.REJECTED, KycStatusDeriver.derive(List.of(
                outcome(KycDocumentType.NATIONAL_ID, KycRecordStatus.REJECTED),
                outcome(KycDocumentType.UTILITY_BILL, KycRecordStatus.REJECTED))));
    }
}
