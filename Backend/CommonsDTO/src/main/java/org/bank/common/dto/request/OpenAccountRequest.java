package org.bank.common.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Opens an account for an existing customer under a catalogue product. */
public record OpenAccountRequest(
        @NotNull UUID customerId,
        @NotBlank @Size(max = 30) @Pattern(regexp = "[A-Za-z0-9_-]+", message = "must contain only letters, digits, '_' or '-'") String productCode,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be an upper-case ISO-4217 code") String currency,
        @NotBlank @Size(max = 20) String branchCode) {
}
