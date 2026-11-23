package org.bank.ledger.service;

import java.math.BigDecimal;
import java.util.UUID;

/** Net effect of a batch on one account (credits minus debits). */
public record AccountDelta(UUID accountId, String currency, BigDecimal net) {
}
