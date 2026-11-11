package org.bank.customer.repository;

import jakarta.validation.constraints.Size;
import org.bank.common.dto.enums.CustomerKycStatus;
import org.bank.common.dto.enums.CustomerStatus;
import org.bank.common.dto.enums.RiskLevel;

/** Query-string filters for GET /api/v1/customers. The branch is overridden for branch-restricted callers. */
public record CustomerSearchCriteria(
        @Size(max = 100) String q,
        CustomerStatus status,
        CustomerKycStatus kycStatus,
        RiskLevel riskLevel,
        @Size(max = 20) String branchCode
) {
}
