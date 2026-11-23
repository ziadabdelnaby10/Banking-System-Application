package org.bank.ledger.repository;

import java.math.BigDecimal;

/** Stored balance next to the balance recomputed from entries, both read by a single statement. */
public interface ReconciliationRow {

    String getCurrency();

    BigDecimal getStored();

    BigDecimal getComputed();
}
