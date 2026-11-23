package org.bank.ledger.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bank.common.dto.enums.JournalBatchStatus;
import org.bank.common.util.UuidV7Generator;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Header of an atomically posted, balanced set of entries. Immutable (Hibernate skips dirty checking and the
 * database rejects updates). No associations: its entries are read by {@code batch_id} in one explicit query.
 */
@Getter
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "journal_batches")
public class JournalBatch {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Size(max = 40)
    @Column(name = "batch_ref", nullable = false, updatable = false, length = 40)
    private String batchRef;

    @NotNull
    @Size(max = 60)
    @Column(name = "source_service", nullable = false, updatable = false, length = 60)
    private String sourceService;

    @Size(max = 120)
    @Column(name = "source_event", updatable = false, length = 120)
    private String sourceEvent;

    @NotNull
    @Size(max = 64)
    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 20)
    private JournalBatchStatus status;

    @Size(max = 64)
    @Column(name = "correlation_id", updatable = false, length = 64)
    private String correlationId;

    @NotNull
    @Column(name = "posted_by", nullable = false, updatable = false)
    private UUID postedBy;

    @Column(name = "posted_at", nullable = false, updatable = false)
    private OffsetDateTime postedAt;

    public static JournalBatch post(String batchRef, String sourceService, String sourceEvent, String requestHash,
                                    String correlationId, UUID postedBy, OffsetDateTime postedAt) {
        JournalBatch batch = new JournalBatch();
        batch.batchRef = batchRef;
        batch.sourceService = sourceService;
        batch.sourceEvent = sourceEvent;
        batch.requestHash = requestHash;
        batch.status = JournalBatchStatus.POSTED;
        batch.correlationId = correlationId;
        batch.postedBy = postedBy;
        batch.postedAt = postedAt;
        return batch;
    }

    // Assigned on persist (not in the factory) so Spring Data sees a new entity and inserts without a prior SELECT.
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UuidV7Generator.generateUUIDV7();
        }
    }
}
