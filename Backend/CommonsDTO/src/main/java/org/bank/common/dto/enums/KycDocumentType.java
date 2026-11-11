package org.bank.common.dto.enums;

public enum KycDocumentType {
    NATIONAL_ID(true),
    PASSPORT(true),
    DRIVING_LICENSE(false),
    UTILITY_BILL(false),
    PROOF_OF_INCOME(false);

    private final boolean proofOfIdentity;

    KycDocumentType(boolean proofOfIdentity) {
        this.proofOfIdentity = proofOfIdentity;
    }

    /** Only identity documents can verify a customer; supporting documents (bills, income) cannot. */
    public boolean isProofOfIdentity() {
        return proofOfIdentity;
    }
}
