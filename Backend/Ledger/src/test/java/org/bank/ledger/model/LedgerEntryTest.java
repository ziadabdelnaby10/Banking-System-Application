package org.bank.ledger.model;

import org.bank.common.dto.enums.EntryType;
import org.bank.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

class LedgerEntryTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-18T10:00:00Z");

    @Test
    void createNormalisesTheAmountToTwoDecimals() {
        LedgerEntry entry = create(EntryType.CREDIT, "12.5");

        assertAll(
                () -> assertEquals(new BigDecimal("12.50"), entry.getAmount()),
                () -> assertEquals(1, entry.getLineNo()));
    }

    @Test
    void creditsIncreaseAndDebitsDecreaseTheBalance() {
        assertAll(
                () -> assertEquals(new BigDecimal("5.00"), create(EntryType.CREDIT, "5.00").signedAmount()),
                () -> assertEquals(new BigDecimal("-5.00"), create(EntryType.DEBIT, "5.00").signedAmount()));
    }

    @Test
    void nonPositiveAmountsAreRejected() {
        assertAll(
                () -> assertThrowsExactly(BusinessRuleException.class, () -> create(EntryType.DEBIT, "0")),
                () -> assertThrowsExactly(BusinessRuleException.class, () -> create(EntryType.DEBIT, "-1.00")));
    }

    @Test
    void amountsAreNeverSilentlyRounded() {
        assertThrowsExactly(BusinessRuleException.class, () -> create(EntryType.CREDIT, "1.001"));
    }

    private static LedgerEntry create(EntryType type, String amount) {
        return LedgerEntry.create(UUID.randomUUID(), 1, UUID.randomUUID(), type, new BigDecimal(amount), "EGP", null, NOW);
    }
}
