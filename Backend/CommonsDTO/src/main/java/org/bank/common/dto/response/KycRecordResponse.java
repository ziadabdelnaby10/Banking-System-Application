package org.bank.common.dto.response;

import org.bank.common.dto.enums.KycDocumentType;
import org.bank.common.dto.enums.KycRecordStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record KycRecordResponse(
        UUID id,
        UUID customerId,
        KycDocumentType documentType,
        String documentRef,
        KycRecordStatus status,
        UUID submittedBy,
        OffsetDateTime submittedAt,
        UUID reviewedBy,
        OffsetDateTime reviewedAt,
        String reviewNotes
) {
}
