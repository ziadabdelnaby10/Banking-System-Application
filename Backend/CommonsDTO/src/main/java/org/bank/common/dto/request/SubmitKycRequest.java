package org.bank.common.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.bank.common.dto.enums.KycDocumentType;

public record SubmitKycRequest(
        @NotNull KycDocumentType documentType,
        @NotBlank @Size(max = 120) String documentRef
) {
}
