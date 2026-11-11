# Customer service

Branches, customers and KYC. Port `9002`, routed by the Gateway under `/api/v1/branches/**` and `/api/v1/customers/**`.

Authentication is done by [CommonSecurity](../CommonSecurity/README.md): RS256 JWTs issued by Identity are validated against
Identity's JWKS (`bank.security.jwt.*`). Authorization uses permission authorities plus branch scoping
(`@branchAccess.canAccess(...)`; ADMIN/AUDITOR bypass). Out-of-branch resources answer `404`.

## Endpoints

| Method | Path | Permission |
|---|---|---|
| POST / GET / GET{code} / PATCH{code} | `/api/v1/branches` | BRANCH_MANAGE (write), BRANCH_VIEW (read) |
| POST / GET | `/api/v1/customers` | CUSTOMER_CREATE / CUSTOMER_VIEW |
| GET / PATCH | `/api/v1/customers/{id}` | CUSTOMER_VIEW / CUSTOMER_UPDATE |
| PATCH | `/api/v1/customers/{id}/status` | CUSTOMER_STATUS_CHANGE |
| POST / GET | `/api/v1/customers/{id}/kyc` | KYC_SUBMIT / CUSTOMER_VIEW |
| POST | `/api/v1/customers/{id}/kyc/{recordId}/review` | KYC_REVIEW (reviewer must differ from submitter) |

The permissions are seeded by Identity migration `V4__customer_permissions.sql`.

## Design notes

- Ids are UUIDv7; customers reference branches by `branch_code`.
- Entities have no JPA associations (reference by id/code), lists use one page query plus one count, and
  `default_batch_fetch_size` is set: no N+1. `CustomerApiIntegrationTest.QueryCounts` asserts the statement counts.
- The national id is never stored: only an HMAC-SHA256 fingerprint (pepper `bank.customer.national-id-pepper`) for
  uniqueness and the last 4 characters for display.
- KYC maker-checker is enforced in the domain and by a DB check constraint; one pending record per customer and document type.
- Domain events (customer registered, status changed, KYC submitted/reviewed) are written to `outbox_events` in the same
  transaction. No publisher yet.
- Optimistic locking (`@Version`); a conflict returns `409`.

## Run

Dev profile needs Postgres on `localhost:5434` (database `bank_customer`) and uses `IDENTITY_JWT_ISSUER`,
`IDENTITY_JWK_SET_URI`, `CUSTOMER_NATIONAL_ID_PEPPER` (dev defaults exist; set real values elsewhere).

```
./mvnw spring-boot:run
./mvnw test        # integration tests use Testcontainers (Docker required)
```

Integration tests mint tokens with Identity's claim contract (`support/TestIdentity`) and serve a local JWKS, proving that
Identity tokens are accepted, and expired / wrong-issuer / forged tokens are rejected.
