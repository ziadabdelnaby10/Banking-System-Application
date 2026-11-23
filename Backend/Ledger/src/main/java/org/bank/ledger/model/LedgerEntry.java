package org.bank.ledger.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bank.common.dto.enums.EntryType;
import org.bank.common.exception.BusinessRuleException;
import org.bank.common.util.UuidV7Generator;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One line of the append-only journal. Never updated or deleted: a correction is a new, opposite entry. The owning
 * batch and the account are plain ids, so reading entries never triggers lazy loading.
 */
@Getter
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "ledger_entries", indexes = {
        @Index(name = "ledger_entries_account_posted_idx", columnList = "account_id, posted_at desc, id desc")
})
public class LedgerEntry {

    public static final int MONEY_SCALE = 2;

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "batch_id", nullable = false, updatable = false)
    private UUID batchId;

    @Column(name = "line_no", nullable = false, updatable = false)
    private int lineNo;

    @NotNull
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false, length = 10)
    private EntryType entryType;

    @NotNull
    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @NotNull
    @Size(min = 3, max = 3)
    @Column(name = "currency", nullable = false, updatable = false, length = 3, columnDefinition = "char(3)")
    private String currency;

    @Size(max = 140)
    @Column(name = "reference", updatable = false, length = 140)
    private String reference;

    @Column(name = "posted_at", nullable = false, updatable = false)
    private OffsetDateTime postedAt;

    /**
     * @throws BusinessRuleException if the amount is not positive or has more than two decimals (amounts are never
     *                               rounded silently)
     */
    public static LedgerEntry create(UUID batchId, int lineNo, UUID accountId, EntryType entryType, BigDecimal amount,
                                     String currency, String reference, OffsetDateTime postedAt) {
        if (amount.signum() <= 0) {
            throw new BusinessRuleException("Entry amounts must be positive");
        }
        LedgerEntry entry = new LedgerEntry();
        entry.batchId = batchId;
        entry.lineNo = lineNo;
        entry.accountId = accountId;
        entry.entryType = entryType;
        entry.amount = toMoney(amount);
        entry.currency = currency;
        entry.reference = reference;
        entry.postedAt = postedAt;
        return entry;
    }

    /** Effect on the account balance: positive for a credit, negative for a debit. */
    public BigDecimal signedAmount() {
        return entryType == EntryType.CREDIT ? amount : amount.negate();
    }

    public static BigDecimal toMoney(BigDecimal amount) {
        try {
            return amount.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new BusinessRuleException("Amounts may have at most " + MONEY_SCALE + " decimal places");
        }
    }

    // Assigned on persist (not in the factory) so Spring Data sees a new entity and inserts without a prior SELECT.
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UuidV7Generator.generateUUIDV7();
        }
    }
}
