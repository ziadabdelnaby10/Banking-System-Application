package org.bank.common.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MfaVerifyRequest(
        @NotBlank @Size(max = 200) String challengeToken,
        @NotBlank @Pattern(regexp = "\\d{6,10}", message = "must be a numeric code") String code
) {

    @Override
    public String toString() {
        return "MfaVerifyRequest[challengeToken=***, code=***]";
    }
}
