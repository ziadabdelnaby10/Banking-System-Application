package org.bank.common.dto.response;

import org.bank.common.dto.enums.CustomerKycStatus;
import org.bank.common.dto.enums.CustomerStatus;
import org.bank.common.dto.enums.RiskLevel;

import java.time.OffsetDateTime;
import java.util.UUID;

/** List row. Contains no collections, so mapping a page never triggers additional queries. */
public record CustomerSummaryResponse(
        UUID id,
        String customerNo,
        String fullName,
        String nationalId,
        String branchCode,
        CustomerStatus status,
        CustomerKycStatus kycStatus,
        RiskLevel riskLevel,
        OffsetDateTime createdAt
) {
}
