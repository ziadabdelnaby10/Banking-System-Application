package org.bank.common.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateBranchRequest(
        @NotBlank @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "must contain only letters, digits, '_' or '-'") String code,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 100) String region
) {
}
