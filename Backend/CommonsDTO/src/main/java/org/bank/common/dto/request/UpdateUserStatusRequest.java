package org.bank.common.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.bank.common.dto.enums.UserStatus;


/** The reason is mandatory: status changes are privileged actions and must be justifiable in audits. */
public record UpdateUserStatusRequest(
        @NotNull UserStatus status,
        @NotBlank @Size(max = 255) String reason
) {
}
