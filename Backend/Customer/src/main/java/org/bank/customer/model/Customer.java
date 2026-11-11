package org.bank.customer.model;

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
import org.bank.common.dto.enums.CustomerKycStatus;
import org.bank.common.dto.enums.CustomerStatus;
import org.bank.common.dto.enums.RiskLevel;
import org.bank.common.exception.BusinessRuleException;
import org.bank.common.util.UuidV7Generator;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A bank customer. Deliberately has no JPA associations: the branch is referenced by its code and KYC
 * records / status history point back by customer id. Every read is therefore a single explicit query and
 * lazy loading (the source of N+1 problems) cannot happen.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "customers", indexes = {
        @Index(name = "customers_branch_created_idx", columnList = "branch_code, created_at, id"),
        @Index(name = "customers_branch_status_idx", columnList = "branch_code, status")
})
public class Customer extends AuditableEntity {

    private static final String CUSTOMER_NO_PREFIX = "CUS";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Size(max = 30)
    @Column(name = "customer_no", nullable = false, updatable = false, length = 30)
    private String customerNo;

    @NotNull
    @Size(max = 200)
    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /** Keyed hash of the national id: enforces uniqueness without storing the id itself. */
    @NotNull
    @Size(max = 64)
    @Column(name = "national_id_fingerprint", nullable = false, updatable = false, length = 64)
    private String nationalIdFingerprint;

    @NotNull
    @Size(max = 4)
    @Column(name = "national_id_last4", nullable = false, updatable = false, length = 4)
    private String nationalIdLast4;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel = RiskLevel.LOW;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CustomerStatus status = CustomerStatus.ACTIVE;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 20)
    private CustomerKycStatus kycStatus = CustomerKycStatus.PENDING;

    @NotNull
    @Size(max = 20)
    @Column(name = "branch_code", nullable = false, updatable = false, length = 20)
    private String branchCode;

    public static Customer register(long sequenceNumber, String fullName, LocalDate dateOfBirth,
                                    String nationalIdFingerprint, String nationalIdLast4, String branchCode) {
        Customer customer = new Customer();
        customer.customerNo = formatCustomerNo(sequenceNumber);
        customer.fullName = fullName.trim();
        customer.dateOfBirth = dateOfBirth;
        customer.nationalIdFingerprint = nationalIdFingerprint;
        customer.nationalIdLast4 = nationalIdLast4;
        customer.branchCode = branchCode;
        return customer;
    }

    static String formatCustomerNo(long sequenceNumber) {
        return CUSTOMER_NO_PREFIX + String.format("%010d", sequenceNumber);
    }

    /** PATCH semantics: null means "leave unchanged". Closed customers are read-only. */
    public void updateProfile(String newFullName, LocalDate newDateOfBirth) {
        requireNotClosed("modified");
        if (newFullName != null) {
            this.fullName = newFullName.trim();
        }
        if (newDateOfBirth != null) {
            this.dateOfBirth = newDateOfBirth;
        }
    }

    public void changeRiskLevel(RiskLevel newRiskLevel) {
        requireNotClosed("modified");
        this.riskLevel = newRiskLevel;
    }

    /**
     * Moves the customer to {@code target}.
     *
     * @return the status the customer had before the change (for the audit trail)
     * @throws BusinessRuleException if the transition is not allowed
     */
    public CustomerStatus changeStatus(CustomerStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new BusinessRuleException("A " + status + " customer cannot become " + target);
        }
        CustomerStatus previous = this.status;
        this.status = target;
        return previous;
    }

    public void requireNotClosed(String action) {
        if (status == CustomerStatus.CLOSED) {
            throw new BusinessRuleException("A closed customer cannot be " + action);
        }
    }

    public void applyKycStatus(CustomerKycStatus newKycStatus) {
        this.kycStatus = newKycStatus;
    }

    /** National id shown with everything but the last digits hidden. */
    public String maskedNationalId() {
        return "****" + nationalIdLast4;
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UuidV7Generator.generateUUIDV7();
        }
    }
}
