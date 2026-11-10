package org.bank.customer.model;

import org.bank.common.dto.enums.KycDocumentType;
import org.bank.common.dto.enums.KycRecordStatus;
import org.bank.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

class KycRecordTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-07T10:00:00Z");
    private final UUID maker = UUID.randomUUID();
    private final UUID checker = UUID.randomUUID();

    private KycRecord pending() {
        return KycRecord.submit(UUID.randomUUID(), KycDocumentType.NATIONAL_ID, " ARCH-1 ", maker, NOW);
    }

    @Test
    void submittedRecordIsPendingWithoutReview() {
        KycRecord record = pending();

        assertAll(
                () -> assertEquals(KycRecordStatus.PENDING, record.getStatus()),
                () -> assertEquals("ARCH-1", record.getDocumentRef()),
                () -> assertNull(record.getReviewedBy()),
                () -> assertNull(record.getReviewedAt()));
    }

    @Test
    void checkerCanApprove() {
        KycRecord record = pending();

        record.approve(checker, "Looks genuine", NOW.plusHours(1));

        assertAll(
                () -> assertEquals(KycRecordStatus.APPROVED, record.getStatus()),
                () -> assertEquals(checker, record.getReviewedBy()),
                () -> assertEquals(NOW.plusHours(1), record.getReviewedAt()),
                () -> assertEquals("Looks genuine", record.getReviewNotes()));
    }

    @Test
    void submitterCannotReviewTheirOwnEvidence() {
        KycRecord record = pending();

        assertAll(
                () -> assertThrowsExactly(BusinessRuleException.class, () -> record.approve(maker, null, NOW)),
                () -> assertThrowsExactly(BusinessRuleException.class, () -> record.reject(maker, "no", NOW)),
                () -> assertEquals(KycRecordStatus.PENDING, record.getStatus(), "a refused review leaves the record untouched"));
    }

    @Test
    void rejectionRequiresAReason() {
        KycRecord record = pending();

        assertAll(
                () -> assertThrowsExactly(BusinessRuleException.class, () -> record.reject(checker, null, NOW)),
                () -> assertThrowsExactly(BusinessRuleException.class, () -> record.reject(checker, "   ", NOW)),
                () -> assertEquals(KycRecordStatus.PENDING, record.getStatus()));
    }

    @Test
    void decidedRecordsAreImmutable() {
        KycRecord approved = pending();
        approved.approve(checker, null, NOW);
        KycRecord rejected = pending();
        rejected.reject(checker, "Blurry scan", NOW);
        UUID anotherChecker = UUID.randomUUID();

        assertAll(
                () -> assertThrowsExactly(BusinessRuleException.class, () -> approved.reject(anotherChecker, "changed my mind", NOW)),
                () -> assertThrowsExactly(BusinessRuleException.class, () -> rejected.approve(anotherChecker, null, NOW)),
                () -> assertEquals(KycRecordStatus.APPROVED, approved.getStatus()),
                () -> assertEquals(KycRecordStatus.REJECTED, rejected.getStatus()));
    }
}
