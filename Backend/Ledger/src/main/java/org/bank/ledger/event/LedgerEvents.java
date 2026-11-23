package org.bank.ledger.event;

import java.util.UUID;

/**
 * Outbox event names and payloads. The balance event carries {@code ApplyBalanceSnapshotRequest} (the contract
 * Account consumes); batch events carry ids and counts only: no amounts, no personal data.
 */
public final class LedgerEvents {

    public static final String BATCH_AGGREGATE = "JournalBatch";
    public static final String BALANCE_AGGREGATE = "AccountBalance";

    public static final String BATCH_POSTED = "ledger.batch-posted";
    public static final String BALANCE_UPDATED = "ledger.balance-updated";

    private LedgerEvents() {
    }

    public record BatchPosted(UUID batchId, String batchRef, String sourceService, String sourceEvent,
                              int entryCount, UUID postedBy) {
    }
}
