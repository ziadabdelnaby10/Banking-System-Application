package org.bank.ledger.repository;

import org.bank.common.dto.enums.EntryType;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

/**
 * Optional filters of an account statement. {@code from} is inclusive and {@code to} exclusive, so consecutive
 * statements can be chained without overlap.
 */
public record LedgerEntrySearchCriteria(
        EntryType entryType,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
}
