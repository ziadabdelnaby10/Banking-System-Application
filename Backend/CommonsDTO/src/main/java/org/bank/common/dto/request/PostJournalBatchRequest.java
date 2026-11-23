package org.bank.common.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Posts a balanced set of entries atomically. {@code batchRef} is the caller's idempotency key: replaying the same
 * reference with the same content returns the original batch, with different content it is a conflict.
 */
public record PostJournalBatchRequest(
        @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9._:-]+", message = "must contain only letters, digits, '.', '_', ':' or '-'") String batchRef,
        @NotBlank @Size(max = 60) String sourceService,
        @Size(max = 120) String sourceEvent,
        @NotNull @Size(min = 2, max = 200) List<@NotNull @Valid JournalEntryRequest> entries) {
}
