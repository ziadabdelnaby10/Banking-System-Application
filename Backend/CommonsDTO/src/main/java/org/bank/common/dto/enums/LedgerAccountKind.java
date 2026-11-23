package org.bank.common.dto.enums;

/**
 * Kind of account tracked by the ledger. CUSTOMER balances can never go negative; SYSTEM accounts (cash, clearing,
 * fees) are the bank's own and may be overdrawn because they mirror the opposite side of customer movements.
 */
public enum LedgerAccountKind {
    CUSTOMER,
    SYSTEM
}
