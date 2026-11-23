package org.bank.ledger.service;

import org.bank.common.dto.request.JournalEntryRequest;
import org.bank.common.dto.request.PostJournalBatchRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

/**
 * SHA-256 of a request's content, independent of the order of its entries. It tells an idempotent replay (same
 * batch reference, same hash) apart from a different request that reuses the reference.
 */
@Component
public class BatchFingerprint {

    public String of(PostJournalBatchRequest request) {
        String canonical = Stream.concat(
                        Stream.of(request.sourceService().trim(), request.sourceEvent() == null ? "" : request.sourceEvent().trim()),
                        request.entries().stream().map(BatchFingerprint::line).sorted())
                .collect(joining("\n"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", e);
        }
    }

    private static String line(JournalEntryRequest entry) {
        BigDecimal amount = entry.amount().stripTrailingZeros();
        return entry.accountId() + "|" + entry.entryType() + "|" + amount.toPlainString() + "|" + entry.currency()
                + "|" + (entry.reference() == null ? "" : entry.reference().trim());
    }
}
