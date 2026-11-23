package org.bank.ledger.service;

import org.bank.common.dto.enums.EntryType;
import org.bank.common.dto.request.JournalEntryRequest;
import org.bank.common.dto.request.PostJournalBatchRequest;
import org.bank.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JournalBatchValidatorTest {

    private final JournalBatchValidator validator = new JournalBatchValidator();
    private final UUID cash = UUID.randomUUID();
    private final UUID customer = UUID.randomUUID();

    @Test
    void balancedBatchIsAccepted() {
        assertDoesNotThrow(() -> validator.validate(batch(
                entry(cash, EntryType.DEBIT, "100.00", "EGP"),
                entry(customer, EntryType.CREDIT, "100", "EGP"))));
    }

    @Test
    void batchWithSeveralLegsBalancesAsASum() {
        UUID fees = UUID.randomUUID();
        assertDoesNotThrow(() -> validator.validate(batch(
                entry(customer, EntryType.DEBIT, "101.50", "EGP"),
                entry(cash, EntryType.CREDIT, "100.00", "EGP"),
                entry(fees, EntryType.CREDIT, "1.50", "EGP"))));
    }

    @Test
    void unbalancedBatchIsRejectedWithTheDifference() {
        BusinessRuleException ex = assertThrowsExactly(BusinessRuleException.class, () -> validator.validate(batch(
                entry(cash, EntryType.DEBIT, "100.00", "EGP"),
                entry(customer, EntryType.CREDIT, "99.99", "EGP"))));

        assertTrue(ex.getMessage().contains("0.01"), ex.getMessage());
    }

    @Test
    void eachCurrencyMustBalanceOnItsOwn() {
        assertThrowsExactly(BusinessRuleException.class, () -> validator.validate(batch(
                entry(cash, EntryType.DEBIT, "100.00", "EGP"),
                entry(customer, EntryType.CREDIT, "100.00", "USD"))));
    }

    @Test
    void anAccountCannotAppearInTwoCurrenciesInOneBatch() {
        assertThrowsExactly(BusinessRuleException.class, () -> validator.validate(batch(
                entry(customer, EntryType.DEBIT, "10.00", "EGP"),
                entry(cash, EntryType.CREDIT, "10.00", "EGP"),
                entry(customer, EntryType.DEBIT, "10.00", "USD"),
                entry(cash, EntryType.CREDIT, "10.00", "USD"))));
    }

    private static PostJournalBatchRequest batch(JournalEntryRequest... entries) {
        return new PostJournalBatchRequest("REF-1", "transaction-service", "test", List.of(entries));
    }

    private static JournalEntryRequest entry(UUID account, EntryType type, String amount, String currency) {
        return new JournalEntryRequest(account, type, new BigDecimal(amount), currency, null);
    }
}
