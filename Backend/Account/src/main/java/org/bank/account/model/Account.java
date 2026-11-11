package org.bank.account.model;

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
import org.bank.common.dto.enums.AccountStatus;
import org.bank.common.exception.BusinessRuleException;
import org.bank.common.util.UuidV7Generator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * A bank account. Deliberately has no JPA associations: the owner is referenced by customer id, the branch by its
 * code and the product by id. Every read is therefore a single explicit query and lazy loading (the source of N+1
 * problems) cannot happen.
 *
 * <p>Balances are a read-model of the Ledger, which owns the truth. They are only replaced through
 * {@code AccountRepository#applyBalanceSnapshot}; nothing in this class debits or credits them.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "accounts", indexes = {
        @Index(name = "accounts_customer_status_idx", columnList = "customer_id, status"),
        @Index(name = "accounts_branch_created_idx", columnList = "branch_code, created_at, id")
})
public class Account extends AuditableEntity {

    public static final int MONEY_SCALE = 2;
    private static final String ACCOUNT_NO_PREFIX = "ACC";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Size(max = 30)
    @Column(name = "account_no", nullable = false, updatable = false, length = 30)
    private String accountNo;

    @NotNull
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @NotNull
    @Size(max = 20)
    @Column(name = "branch_code", nullable = false, updatable = false, length = 20)
    private String branchCode;

    @NotNull
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @NotNull
    @Size(min = 3, max = 3)
    @Column(name = "currency", nullable = false, updatable = false, length = 3, columnDefinition = "char(3)")
    private String currency;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;

    @NotNull
    @Column(name = "ledger_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal ledgerBalance = zero();

    @NotNull
    @Column(name = "available_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal availableBalance = zero();

    /** Ledger's monotonic sequence of the last applied balance snapshot. */
    @Column(name = "balance_version", nullable = false)
    private long balanceVersion;

    @Column(name = "balance_as_of", nullable = false)
    private OffsetDateTime balanceAsOf;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private OffsetDateTime openedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    /**
     * Opens an ACTIVE account with a zero balance.
     *
     * @throws BusinessRuleException if the product is retired
     */
    public static Account open(long sequenceNumber, UUID customerId, String branchCode, ProductView product,
                               String currency, OffsetDateTime now) {
        if (!product.active()) {
            throw new BusinessRuleException("Product " + product.code() + " is not available for new accounts");
        }
        Account account = new Account();
        account.accountNo = formatAccountNo(sequenceNumber);
        account.customerId = customerId;
        account.branchCode = normalizeBranchCode(branchCode);
        account.productId = product.id();
        account.currency = currency;
        account.balanceAsOf = now;
        account.openedAt = now;
        return account;
    }

    static String formatAccountNo(long sequenceNumber) {
        return ACCOUNT_NO_PREFIX + String.format("%012d", sequenceNumber);
    }

    /** Branch codes are canonical upper case, matching the {@code branch} claim and the Customer service. */
    public static String normalizeBranchCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    /** ACTIVE -> FROZEN. @return the previous status (for the audit trail) */
    public AccountStatus freeze() {
        if (status != AccountStatus.ACTIVE) {
            throw new BusinessRuleException("Only an ACTIVE account can be frozen, this one is " + status);
        }
        return transitionTo(AccountStatus.FROZEN);
    }

    /** FROZEN -> ACTIVE. @return the previous status (for the audit trail) */
    public AccountStatus unfreeze() {
        if (status != AccountStatus.FROZEN) {
            throw new BusinessRuleException("Only a FROZEN account can be unfrozen, this one is " + status);
        }
        return transitionTo(AccountStatus.ACTIVE);
    }

    /**
     * Closes the account; terminal. Funds must have been withdrawn first.
     *
     * @return the previous status (for the audit trail)
     */
    public AccountStatus close(OffsetDateTime now) {
        if (!status.canTransitionTo(AccountStatus.CLOSED)) {
            throw new BusinessRuleException("A " + status + " account cannot be closed");
        }
        if (ledgerBalance.signum() != 0 || availableBalance.signum() != 0) {
            throw new BusinessRuleException("An account with a non-zero balance cannot be closed");
        }
        AccountStatus previous = transitionTo(AccountStatus.CLOSED);
        this.closedAt = now;
        return previous;
    }

    /** Rounds nothing: amounts with more than two decimals are rejected rather than silently altered. */
    public static BigDecimal toMoney(BigDecimal amount) {
        try {
            return amount.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new BusinessRuleException("Amounts may have at most " + MONEY_SCALE + " decimal places");
        }
    }

    private AccountStatus transitionTo(AccountStatus target) {
        AccountStatus previous = this.status;
        this.status = target;
        return previous;
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }

    // Assigned on persist (not in the factory) so Spring Data sees a new entity and inserts without a prior SELECT.
    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UuidV7Generator.generateUUIDV7();
        }
    }
}
