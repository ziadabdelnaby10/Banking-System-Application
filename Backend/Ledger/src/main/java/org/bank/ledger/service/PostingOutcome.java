package org.bank.ledger.service;

import org.bank.common.dto.response.JournalBatchResponse;

/**
 * Result of posting a batch.
 *
 * @param batch   the posted batch (the original one on a replay)
 * @param created {@code false} when the batch reference had already been posted with identical content
 */
public record PostingOutcome(JournalBatchResponse batch, boolean created) {
}
