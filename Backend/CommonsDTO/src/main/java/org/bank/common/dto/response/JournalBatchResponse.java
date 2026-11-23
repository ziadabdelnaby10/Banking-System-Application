package org.bank.common.dto.response;

import org.bank.common.dto.enums.JournalBatchStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** A posted journal batch with its entries in posting order. */
public record JournalBatchResponse(
        UUID id,
        String batchRef,
        String sourceService,
        String sourceEvent,
        JournalBatchStatus status,
        UUID postedBy,
        OffsetDateTime postedAt,
        List<LedgerEntryResponse> entries
) {
}
