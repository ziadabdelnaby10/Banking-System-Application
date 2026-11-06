# Database migration layout

This project now follows a microservices-first architecture.

Each service owns its own database and its own migration history. Cross-service references are logical IDs or codes rather than direct foreign keys across databases.

## Service ownership

- `identity/` — users, roles, permissions, MFA, sessions
- `customer/` — branches, customers, KYC metadata
- `account/` — accounts and account lifecycle state
- `transaction/` — payment/transfer requests and idempotency keys
- `ledger/` — ledger entries, balances, and journal batches
- `notification/` — notification delivery, template metadata, delivery attempts
- `approval/` — maker-checker tasks and approval decisions
- `audit/` — immutable audit records and event storage
- `reporting/` — reporting jobs and aggregated metrics

## Database naming convention

Use one PostgreSQL database per service using `bank_<service>` names:

- `bank_identity`
- `bank_customer`
- `bank_account`
- `bank_transaction`
- `bank_ledger`
- `bank_notification`
- `bank_approval`
- `bank_audit`
- `bank_reporting`

Canonical JDBC pattern:

`jdbc:postgresql://postgres:5432/bank_<service>`

