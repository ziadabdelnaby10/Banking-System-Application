package org.bank.ledger.service;

import org.bank.common.dto.enums.EntryType;
import org.bank.common.dto.request.JournalEntryRequest;
import org.bank.common.dto.request.PostJournalBatchRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BalanceDeltasAndFingerprintTest {

    private final BatchFingerprint fingerprint = new BatchFingerprint();
    private final UUID low = new UUID(0, 1);
    private final UUID high = new UUID(0, 2);

    @Test
    void deltasAreNetPerAccountAndSortedByAccountId() {
        List<AccountDelta> deltas = BalanceDeltas.of(List.of(
                entry(high, EntryType.CREDIT, "30.00"),
                entry(low, EntryType.DEBIT, "100.00"),
                entry(high, EntryType.CREDIT, "70.00")));

        assertAll(
                () -> assertEquals(2, deltas.size()),
                () -> assertEquals(low, deltas.get(0).accountId()),
                () -> assertEquals(0, new BigDecimal("-100.00").compareTo(deltas.get(0).net())),
                () -> assertEquals(high, deltas.get(1).accountId()),
                () -> assertEquals(0, new BigDecimal("100.00").compareTo(deltas.get(1).net())));
    }

    @Test
    void anAccountWhoseLegsCancelOutNeedsNoBalanceChange() {
        List<AccountDelta> deltas = BalanceDeltas.of(List.of(
                entry(low, EntryType.DEBIT, "10.00"),
                entry(low, EntryType.CREDIT, "10.00"),
                entry(high, EntryType.CREDIT, "5.00"),
                entry(low, EntryType.DEBIT, "5.00")));

        assertEquals(2, deltas.size());
    }

    @Test
    void fingerprintIgnoresEntryOrderAndTrailingZeros() {
        String first = fingerprint.of(request(
                entry(low, EntryType.DEBIT, "10.00"), entry(high, EntryType.CREDIT, "10.00")));
        String reordered = fingerprint.of(request(
                entry(high, EntryType.CREDIT, "10"), entry(low, EntryType.DEBIT, "10.0")));

        assertAll(
                () -> assertEquals(first, reordered),
                () -> assertEquals(64, first.length()),
                () -> assertTrue(first.matches("[0-9a-f]{64}")));
    }

    @Test
    void fingerprintChangesWithAnyContentChange() {
        String original = fingerprint.of(request(
                entry(low, EntryType.DEBIT, "10.00"), entry(high, EntryType.CREDIT, "10.00")));

        assertAll(
                () -> assertNotEquals(original, fingerprint.of(request(
                        entry(low, EntryType.DEBIT, "10.01"), entry(high, EntryType.CREDIT, "10.01")))),
                () -> assertNotEquals(original, fingerprint.of(request(
                        entry(high, EntryType.DEBIT, "10.00"), entry(low, EntryType.CREDIT, "10.00")))));
    }

    private static PostJournalBatchRequest request(JournalEntryRequest... entries) {
        return new PostJournalBatchRequest("REF-1", "transaction-service", "transfer.approved", List.of(entries));
    }

    private static JournalEntryRequest entry(UUID account, EntryType type, String amount) {
        return new JournalEntryRequest(account, type, new BigDecimal(amount), "EGP", null);
    }
}
