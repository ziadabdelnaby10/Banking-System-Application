# Database migration layout

This project now follows a microservices-first architecture.

Each service owns its own database and its own migration history. Cross-service references are logical IDs or codes rather than direct foreign keys across databases.

## Service ownership

- `identity/` — users, roles, permissions, MFA, sessions
- `customer/` — branches, customers, KYC metadata
- `account/` — accounts and account lifecycle state
- `transaction/` — payment/transfer requests and idempotency keys
- `ledger/` — ledger entries, balances, and journal batches
- `approval/` — maker-checker tasks and approval decisions
- `audit/` — immutable audit records and event storage
- `reporting/` — reporting jobs and aggregated metrics
