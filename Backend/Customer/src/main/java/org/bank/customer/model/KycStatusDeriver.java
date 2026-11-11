package org.bank.customer.model;

import org.bank.common.dto.enums.CustomerKycStatus;

import java.util.Collection;

/**
 * Single place that decides a customer's overall KYC standing from their KYC records.
 * <ol>
 *   <li>VERIFIED as soon as one identity document (national id, passport) is approved;</li>
 *   <li>otherwise PENDING while anything awaits review, or when nothing was submitted yet;</li>
 *   <li>otherwise REJECTED (everything submitted was rejected).</li>
 * </ol>
 */
public final class KycStatusDeriver {

    private KycStatusDeriver() {
    }

    public static CustomerKycStatus derive(Collection<KycOutcome> outcomes) {
        boolean anyPending = false;
        boolean anyRejected = false;
        for (KycOutcome outcome : outcomes) {
            switch (outcome.status()) {
                case APPROVED -> {
                    if (outcome.documentType().isProofOfIdentity()) {
                        return CustomerKycStatus.VERIFIED;
                    }
                }
                case PENDING -> anyPending = true;
                case REJECTED -> anyRejected = true;
            }
        }
        if (anyPending || !anyRejected) {
            return CustomerKycStatus.PENDING;
        }
        return CustomerKycStatus.REJECTED;
    }
}
