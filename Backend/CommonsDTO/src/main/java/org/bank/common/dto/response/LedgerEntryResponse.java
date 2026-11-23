package org.bank.common.dto.response;

import org.bank.common.dto.enums.EntryType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** An immutable posted ledger line. {@code lineNo} is the 1-based position inside its batch. */
public record LedgerEntryResponse(
        UUID id,
        UUID batchId,
        int lineNo,
        UUID accountId,
        EntryType entryType,
        BigDecimal amount,
        String currency,
        String reference,
        OffsetDateTime postedAt
) {
}
