package org.bank.common.error;

import jakarta.validation.constraints.NotBlank;

public record ApiErrorDetail(
        String field,
        String message
) {
}

