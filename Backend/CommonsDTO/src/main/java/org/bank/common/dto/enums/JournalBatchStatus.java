package org.bank.common.dto.enums;

/** Lifecycle of a journal batch. Posting is atomic, so a stored batch is always POSTED. */
public enum JournalBatchStatus {
    POSTED
}
