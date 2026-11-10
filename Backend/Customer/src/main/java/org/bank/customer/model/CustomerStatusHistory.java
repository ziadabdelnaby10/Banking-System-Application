package org.bank.customer.model;

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
import org.bank.common.dto.enums.CustomerStatus;
import org.bank.common.util.UuidV7Generator;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Append-only audit row written whenever a customer's status changes. Never updated or deleted. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "customer_status_history")
public class CustomerStatusHistory {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, updatable = false, length = 20)
    private CustomerStatus fromStatus;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false, length = 20)
    private CustomerStatus toStatus;

    @NotNull
    @Size(max = 500)
    @Column(name = "reason", nullable = false, updatable = false, length = 500)
    private String reason;

    @NotNull
    @Column(name = "changed_by", nullable = false, updatable = false)
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private OffsetDateTime changedAt;

    @Size(max = 64)
    @Column(name = "correlation_id", updatable = false, length = 64)
    private String correlationId;

    public static CustomerStatusHistory record(UUID customerId, CustomerStatus from, CustomerStatus to, String reason,
                                               UUID changedBy, OffsetDateTime changedAt, String correlationId) {
        CustomerStatusHistory entry = new CustomerStatusHistory();
        entry.customerId = customerId;
        entry.fromStatus = from;
        entry.toStatus = to;
        entry.reason = reason.trim();
        entry.changedBy = changedBy;
        entry.changedAt = changedAt;
        entry.correlationId = correlationId;
        return entry;
    }

    // Assigned on persist (not in the factory) so Spring Data sees a new entity and inserts without a prior SELECT.
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UuidV7Generator.generateUUIDV7();
        }
    }
}
