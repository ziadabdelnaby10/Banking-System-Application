package org.bank.common.dto.enums;

/** Overall KYC standing of a customer, derived from the decisions on their KYC records. */
public enum CustomerKycStatus {
    PENDING,
    VERIFIED,
    REJECTED
}
