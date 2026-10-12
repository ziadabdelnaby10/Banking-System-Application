# ADR-004 Implementation Guide — Double-Entry Ledger Design

Audience: Junior Java / Spring Boot developer

Goal: Implement double-entry ledger posting so each posted financial operation produces matched debit and credit ledger entries as described in `ADR-004-double-entry-ledger.md`.

Prerequisites
- Understanding of basic accounting (debit/credit)
- Database tables `transactions` and `ledger_entries` exist (see migration)

Steps
1. Read the ADR: `Backend/docs/adr/ADR-004-double-entry-ledger.md`.
2. Define domain entities:
   - `Transaction` (already present) and `LedgerEntry` entity mapped to `ledger_entries` table.
3. Implement `LedgerService` with API `postTransaction(TransactionDto)`:
   - Validate transaction state and currency
   - Build two or more `LedgerEntry` rows (debit and credit) where sum(debits) == sum(credits)
   - In a single DB transaction:
     - Insert `transactions` if not present
     - Insert corresponding `ledger_entries`
     - Update account `ledger_balance` and `available_balance` atomically, using optimistic locking (`lock_version` in `accounts` table)
4. Idempotency:
   - Use `idempotency_key` on `transactions` to ensure the same request does not produce duplicate postings.
5. Error handling:
   - If balance checks fail or DB constraints are violated, roll back the transaction and set status `FAILED` on the `transactions` row.
6. Tests:
   - Unit tests for `LedgerService` logic (validate matching entries and amounts).
   - Integration test using Testcontainers to post a transaction and assert ledger entries and balances.
7. Reconciliation endpoint (optional):
   - Add an internal endpoint to recompute balances from `ledger_entries` for a given account and compare to stored `ledger_balance`.

PR checklist
- `LedgerService` implemented and covered by tests.
- Idempotency respected and tested.
- Optimistic locking on accounts is in use and tested under concurrent cases.
