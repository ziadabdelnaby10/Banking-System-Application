package org.bank.common.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.bank.common.dto.enums.CustomerStatus;

public record ChangeCustomerStatusRequest(
        @NotNull CustomerStatus status,
        @NotBlank @Size(max = 500) String reason
) {
}
