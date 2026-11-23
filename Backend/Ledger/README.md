# Ledger service

Append-only double-entry journal and the source of truth for balances. Port `9004`, routed by the Gateway under
`/api/v1/ledger/**`. Authentication is done by [CommonSecurity](../CommonSecurity/README.md).

## Endpoints

| Method | Path | Permission |
|---|---|---|
| POST | `/api/v1/ledger/journal-batches` (201 created, 200 idempotent replay) | LEDGER_POST |
| GET | `/api/v1/ledger/journal-batches/{id}` | LEDGER_VIEW |
| GET | `/api/v1/ledger/accounts/{id}/entries` (filters: `entryType`, `from`, `to`; sort whitelist) | LEDGER_VIEW |
| GET | `/api/v1/ledger/accounts/{id}/balance` | LEDGER_VIEW |
| GET | `/api/v1/ledger/accounts/{id}/reconciliation` (stored vs recomputed balance) | LEDGER_RECONCILE |

Permissions are seeded by Identity migration `V6__ledger_permissions.sql` (ADMIN/MANAGER: all, TELLER: view,
AUDITOR: view + reconcile).

## Design notes

- A batch is posted atomically: at least two entries, debits equal credits per currency, one currency per account. Sign
  convention: CREDIT adds to an account balance, DEBIT subtracts.
- Idempotency: `batchRef` is unique; the same `batchRef` with the same content replays the original (200), with different
  content it is a `409`. A SHA-256 request hash decides.
- Balances are maintained with one conditional native `UPDATE` per account (sorted by account id, so concurrent batches
  cannot deadlock). Customer accounts can never go negative (SQL guard plus a table check). Bank-owned system/GL accounts
  (`bank.ledger.system-accounts`) may. A first posting creates the balance row with `INSERT ... ON CONFLICT DO NOTHING`.
- `ledger_entries` and `journal_batches` are append-only (database triggers reject UPDATE/DELETE/TRUNCATE).
- Entities are immutable with no JPA associations; ids are UUIDv7. A post to existing accounts costs a fixed handful of
  statements (asserted in `LedgerApiIntegrationTest`).
- Balance sync to Account is outbox-only: `ledger.batch-posted` and `ledger.balance-updated` (payload
  `ApplyBalanceSnapshotRequest`, CUSTOMER accounts only) are written in the same transaction. No relay/consumer exists yet.

## Run

Dev profile needs Postgres on `localhost:5434` (database `bank_ledger`). Configuration lives in
`Banking-System-Application-config/ledger`; the schema in `src/main/resources/db/migration` (mirrored in
`infra/db/migration/ledger`).

```
./mvnw spring-boot:run
./mvnw verify      # LedgerApiIntegrationTest uses Testcontainers (Docker required)
```

Production must set `LEDGER_SYSTEM_ACCOUNTS` (comma-separated UUIDs); there is no default.
