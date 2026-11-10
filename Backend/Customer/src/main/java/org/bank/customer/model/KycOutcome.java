package org.bank.customer.model;

import org.bank.common.dto.enums.KycDocumentType;
import org.bank.common.dto.enums.KycRecordStatus;

/** One KYC record reduced to what the customer-level KYC status depends on. */
public record KycOutcome(KycDocumentType documentType, KycRecordStatus status) {
}
