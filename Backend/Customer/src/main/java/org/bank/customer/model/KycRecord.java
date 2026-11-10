package org.bank.customer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bank.common.dto.enums.KycDocumentType;
import org.bank.common.dto.enums.KycRecordStatus;
import org.bank.common.exception.BusinessRuleException;
import org.bank.common.util.UuidV7Generator;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * Evidence submitted for a customer's KYC and the decision taken on it (maker-checker: the person who
 * submitted it can never be the one who decides). A decided record is immutable.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "kyc_records", indexes = @Index(name = "kyc_records_customer_idx", columnList = "customer_id, submitted_at"))
public class KycRecord {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Plain id instead of an association: see {@link Customer} for why. */
    @NotNull
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, updatable = false, length = 30)
    private KycDocumentType documentType;

    /** Reference to the stored evidence (scan id, archive number); the document itself is not kept here. */
    @NotNull
    @Size(max = 120)
    @Column(name = "document_ref", nullable = false, updatable = false, length = 120)
    private String documentRef;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private KycRecordStatus status = KycRecordStatus.PENDING;

    @NotNull
    @Column(name = "submitted_by", nullable = false, updatable = false)
    private UUID submittedBy;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Size(max = 1000)
    @Column(name = "review_notes", length = 1000)
    private String reviewNotes;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static KycRecord submit(UUID customerId, KycDocumentType documentType, String documentRef,
                                   UUID submittedBy, OffsetDateTime now) {
        KycRecord record = new KycRecord();
        record.customerId = customerId;
        record.documentType = documentType;
        record.documentRef = documentRef.trim();
        record.submittedBy = submittedBy;
        record.submittedAt = now;
        return record;
    }

    public void approve(UUID reviewer, String notes, OffsetDateTime now) {
        decide(KycRecordStatus.APPROVED, reviewer, notes, now);
    }

    public void reject(UUID reviewer, String notes, OffsetDateTime now) {
        if (notes == null || notes.isBlank()) {
            throw new BusinessRuleException("A reason is required when rejecting KYC evidence");
        }
        decide(KycRecordStatus.REJECTED, reviewer, notes, now);
    }

    private void decide(KycRecordStatus outcome, UUID reviewer, String notes, OffsetDateTime now) {
        if (status != KycRecordStatus.PENDING) {
            throw new BusinessRuleException("This KYC record was already " + status.name().toLowerCase(Locale.ROOT) + " and cannot be changed");
        }
        if (submittedBy.equals(reviewer)) {
            throw new BusinessRuleException("KYC evidence must be reviewed by someone other than the person who submitted it");
        }
        this.status = outcome;
        this.reviewedBy = reviewer;
        this.reviewedAt = now;
        this.reviewNotes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UuidV7Generator.generateUUIDV7();
        }
    }
}
