package org.bank.account.model;

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
import org.bank.common.dto.enums.AccountStatus;
import org.bank.common.util.UuidV7Generator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Append-only audit row written whenever an account is opened or its status changes. Never updated or deleted.
 * {@code previousStatus} is null for the opening entry.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "account_status_history")
public class AccountStatusHistory {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", updatable = false, length = 20)
    private AccountStatus previousStatus;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, updatable = false, length = 20)
    private AccountStatus newStatus;

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

    public static AccountStatusHistory record(UUID accountId, AccountStatus previous, AccountStatus next, String reason,
                                              UUID changedBy, OffsetDateTime changedAt, String correlationId) {
        AccountStatusHistory entry = new AccountStatusHistory();
        entry.accountId = accountId;
        entry.previousStatus = previous;
        entry.newStatus = next;
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
