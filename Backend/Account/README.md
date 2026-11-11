# Account service

Account products and the account lifecycle. Port `9003`, routed by the Gateway under `/api/v1/accounts/**` and
`/api/v1/account-products/**`.

Authentication is done by [CommonSecurity](../CommonSecurity/README.md): RS256 JWTs issued by Identity are validated against
Identity's JWKS (`bank.security.jwt.*`). Authorization uses permission authorities plus branch scoping
(`@branchAccess.canAccess(...)`; ADMIN/AUDITOR bypass). Out-of-branch accounts answer `404`.

## Endpoints

| Method | Path | Permission |
|---|---|---|
| POST | `/api/v1/accounts` | ACCOUNT_CREATE (branch scoped) |
| GET | `/api/v1/accounts` (filters: `customerId`, `accountNo` prefix, `status`, `branchCode`, `productCode`) | ACCOUNT_VIEW |
| GET | `/api/v1/accounts/{id}`, `/{id}/balance`, `/{id}/status-history` | ACCOUNT_VIEW |
| POST | `/api/v1/accounts/{id}/freeze`, `/unfreeze`, `/close` (body: mandatory `reason`) | ACCOUNT_STATUS_CHANGE |
| GET | `/api/v1/account-products` | ACCOUNT_VIEW |

The permissions are seeded by Identity migration `V5__account_permissions.sql` (TELLER: view+create, MANAGER/ADMIN: all,
AUDITOR: view).

## Design notes

- Layout follows Customer/Identity: `controller`, `service` (+ `service/implementation`), `repository`, `model`, `mapper`
  (MapStruct + Lombok), `config`, `aop`. All DTOs live in `CommonsDTO`.
- Ids are UUIDv7; account numbers are `ACC` + 12 digits from the `account_no_seq` sequence.
- Balances are a read-only snapshot of the Ledger. They change only through `AccountService.applyBalanceSnapshot`, a single
  native conditional `UPDATE` guarded by `balance_version` (stale snapshots are ignored). Closing needs a zero balance.
- Lifecycle: `ACTIVE <-> FROZEN`, `ACTIVE|FROZEN -> CLOSED` (terminal). `PENDING` is reserved for the future Approval flow;
  `AccountServiceImpl.changeStatus` is the seam. Each change writes a history row and an `account.status-changed` outbox event
  in the same transaction (no publisher yet).
- No JPA associations; the product catalogue is cached in memory (`account-products`, Caffeine) as immutable `ProductView`s, so
  `get` costs 1 statement and a page 1 (short page) or 2 (page + count) statements. No N+1.
- Search sorts only on a whitelist (`SafePageable`); `accountNo` filter is a prefix match with LIKE wildcards escaped.
- Optimistic locking (`@Version`); a conflict returns `409`.
- Customer and branch are trusted from the request (no call to the Customer service yet).

## Run

Dev profile needs Postgres on `localhost:5434` (database `bank_account`) and uses `IDENTITY_JWT_ISSUER`, `IDENTITY_JWK_SET_URI`.
Flyway migrations are in `src/main/resources/db/migration` (mirrored in `infra/db/migration/account`).

```
./mvnw spring-boot:run
./mvnw test        # AccountApiIntegrationTest uses Testcontainers (Docker required)
```

`AccountApiIntegrationTest` mints tokens with Identity's claim contract (`support/TestIdentity`), runs against the real
Flyway schema and asserts the per-endpoint statement counts.
