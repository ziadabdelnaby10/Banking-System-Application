package org.bank.customer.event;

import java.util.UUID;

/** Outbox event names and payloads. Payloads carry ids and codes only: no names, no national ids. */
public final class CustomerEvents {

    public static final String CUSTOMER_AGGREGATE = "Customer";
    public static final String BRANCH_AGGREGATE = "Branch";

    public static final String CUSTOMER_REGISTERED = "customer.registered";
    public static final String CUSTOMER_STATUS_CHANGED = "customer.status-changed";
    public static final String KYC_SUBMITTED = "customer.kyc-submitted";
    public static final String KYC_REVIEWED = "customer.kyc-reviewed";
    public static final String BRANCH_CREATED = "branch.created";
    public static final String BRANCH_UPDATED = "branch.updated";

    private CustomerEvents() {
    }

    public record CustomerRegistered(UUID customerId, String customerNo, String branchCode, UUID registeredBy) {
    }

    public record CustomerStatusChanged(UUID customerId, String customerNo, String branchCode,
                                        String fromStatus, String toStatus, String reason, UUID changedBy) {
    }

    public record KycSubmitted(UUID customerId, UUID kycRecordId, String documentType, UUID submittedBy) {
    }

    public record KycReviewed(UUID customerId, UUID kycRecordId, String documentType, String decision,
                              String customerKycStatus, UUID reviewedBy) {
    }

    public record BranchChanged(String code, String name, String status) {
    }
}
