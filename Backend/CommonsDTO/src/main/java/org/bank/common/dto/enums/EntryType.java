package org.bank.common.dto.enums;

/**
 * Side of a double-entry ledger line. A CREDIT increases and a DEBIT decreases the balance of the account it hits,
 * so a customer deposit debits the bank's cash account and credits the customer account.
 */
public enum EntryType {
    DEBIT,
    CREDIT
}
