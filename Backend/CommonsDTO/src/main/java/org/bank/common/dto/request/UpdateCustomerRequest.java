package org.bank.common.dto.request;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import org.bank.common.dto.enums.RiskLevel;

import java.time.LocalDate;

/**
 * PATCH semantics: fields left null are unchanged. Changing the risk level additionally requires the
 * CUSTOMER_STATUS_CHANGE permission (compliance-level decision). The branch and national id cannot be changed here.
 */
public record UpdateCustomerRequest(
        @Size(min = 1, max = 200) String fullName,
        @Past LocalDate dateOfBirth,
        RiskLevel riskLevel
) {
}
