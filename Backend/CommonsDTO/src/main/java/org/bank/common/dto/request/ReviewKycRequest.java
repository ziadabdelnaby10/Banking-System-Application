package org.bank.common.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.bank.common.dto.enums.KycDecision;

/** Checker decision. Notes are mandatory when rejecting. */
public record ReviewKycRequest(
        @NotNull KycDecision decision,
        @Size(max = 1000) String notes
) {
}
