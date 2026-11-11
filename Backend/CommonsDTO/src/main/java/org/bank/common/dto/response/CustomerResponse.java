package org.bank.common.dto.response;

import org.bank.common.dto.enums.CustomerKycStatus;
import org.bank.common.dto.enums.CustomerStatus;
import org.bank.common.dto.enums.RiskLevel;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Full customer view with audit metadata and the KYC records (loaded with one extra query). */
public record CustomerResponse(
        UUID id,
        String customerNo,
        String fullName,
        LocalDate dateOfBirth,
        /** Masked: only the last digits are ever returned. */
        String nationalId,
        String branchCode,
        String branchName,
        CustomerStatus status,
        CustomerKycStatus kycStatus,
        RiskLevel riskLevel,
        OffsetDateTime createdAt,
        UUID createdBy,
        OffsetDateTime updatedAt,
        UUID updatedBy,
        Long version,
        List<KycRecordResponse> kycRecords
) {
}
