package org.bank.common.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.bank.common.dto.enums.EntryType;

import java.math.BigDecimal;
import java.util.UUID;

/** One line of a journal batch: a debit or a credit of a positive amount against a single account. */
public record JournalEntryRequest(
        @NotNull UUID accountId,
        @NotNull EntryType entryType,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be an upper-case ISO-4217 code") String currency,
        @Size(max = 140) String reference) {
}
