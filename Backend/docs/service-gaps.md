# Service Gap Register: Identity, Customer, Account

Missing or deferred work for the three services built so far (plus the shared modules and infra they depend on).
Revisit this list each time a new microservice lands; the "Unblocked by" column says which one closes each gap.

Severity: **High** blocks a production-grade flow or breaks the pipeline. **Medium** is a functional or hardening gap.
**Low** is polish.

Last reviewed: 2026-11-18.

---

## 1. Cross-cutting gaps

| # | Gap | Severity | Evidence | Unblocked by |
|---|-----|----------|----------|--------------|
| X1 | **Outbox events are never published.** Account and Customer write `outbox_events` rows in the business transaction, but no relay publishes them (`published_at` stays `NULL`). No module has a Kafka dependency, and Kafka runs only in compose. | High | `Account/.../service/OutboxRecorder.java`, `Customer/.../service/OutboxRecorder.java`; READMEs ("no publisher yet"); `Account/.../db/migration/V1__init_schema.sql` (outbox table comment); no `spring-kafka` in any `pom.xml` | Outbox relay (scheduled poller or CDC) in each service, then Audit / Notification consumers |
| X2 | **No event contract.** Topic names, payload schema, versioning and dead-letter handling are not defined for `account.opened`, `account.status-changed`, and the Customer/KYC events. | High | `Account/.../event/AccountEvents.java` has event type names only; `docs/contracts.md` has no event section for these | Define in `docs/contracts.md` before building consumers |
| X3 | **No rate limiting anywhere.** HLD promises it at the Gateway and on login; Redis is only a compose container. Account lockout exists in Identity, but there is no request throttling. | High | `docs/hld.md` lines 20, 82, 126, 145; no limiter/bucket code in Identity, Gateway, CommonSecurity | Gateway `RequestRateLimiter` (Redis) plus a stricter limit on `/api/v1/auth/**` |
| X4 | **No idempotency handling.** No `Idempotency-Key` header support on create endpoints (open account, register customer, create user). Only the Transaction schema has an `idempotency_keys` table. | High | `infra/db/migration/transaction/V1__init_schema.sql`; no idempotency code in the three services | Shared filter/table in CommonSecurity or each service; mandatory for Transaction |
| X5 | **CD image build is broken.** `Backend/Dockerfile` copies `mvnw`, `.mvn` and `pom.xml` from the `Backend/` root, which has no pom. The CD workflow builds this file with `context: ./Backend`. | High | `Backend/Dockerfile` lines 4-8; `.github/workflows/cd.yml` | Per-service Dockerfile (or one parameterised by `ARG MODULE`) that builds `CommonsDTO` -> `CommonSecurity` -> service, and a CD matrix per service |
| X6 | **docker-compose is infra only.** It has Postgres, Redis, 3x Kafka and MailHog, but no Config, Discovery, Gateway, Identity, Customer or Account containers. | Medium | `Backend/docker-compose.yml` | Add service containers after X5; use one DB per service (`bank_<service>`) per `infra/db/migration/README.md` |
| X7 | **Observability is minimal.** Actuator exposes `health,info` only; no Micrometer Prometheus registry, no tracing, no business metrics. Correlation ids exist (CommonSecurity). | Medium | `Banking-System-Application-config/*/*-service-dev.yml` (`include: health,info`); no `micrometer-registry-prometheus` / OpenTelemetry in poms | Add registry and OTel; expose `prometheus` only behind internal network |
| X8 | **No PII redaction in logs/error payloads.** API responses mask national id, but log statements and error handlers are not redacted. | Medium | `Customer/.../mapper/CustomerMapper.java` (masking is response-only); no log masking config | Logback converter or structured-logging redaction |
| X9 | **No security response headers.** HSTS, CSP, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy` are not configured on Gateway or services. | Medium | No header configuration in Gateway or CommonSecurity | Gateway default-filters / Spring Security headers (see OWASP guide H1-H7) |
| X10 | **CI coverage limited to built modules.** Matrix has CommonsDTO, CommonSecurity, Config, Discovery, Gateway, Identity, Customer, Account, Ledger. Migrations for Transaction, Approval, Audit, Notification, Reporting exist but are never validated. | Low | `.github/workflows/ci.yml`; `infra/db/migration/*` | Add each service to the matrix when created |
| X11 | **No cross-service contract test.** Nothing proves a JWT issued by Identity is accepted by Customer/Account with the expected claims (roles, permissions, branch). | Medium | Identity tests cover Identity only; Customer/Account use `TestIdentity`/test JWTs | Pact-style or integration test that boots Identity and calls Customer/Account |
| X12 | **Shared module versions are hard-coded per service** (`dto` / `security` `1.1.0` in each `pom.xml`). A bump must be made in six poms and the CI install step. | Low | `Identity/Customer/Account/Gateway/CommonSecurity/pom.xml` | Aggregator or parent pom / BOM in `Backend/` (also fixes X5 and lets CI use `-pl <module> -am`) |

---

## 2. Identity

| # | Gap | Severity | Evidence | Unblocked by |
|---|-----|----------|----------|--------------|
| I1 | **Production MFA delivery.** MFA codes are only logged in dev/test; production delivery fails closed. | High (for prod) | `Identity/README.md` line 68 | Notification service (ADR-007) |
| I2 | **No outbox / domain events.** Login success/failure, lockout, role and status changes are recorded only in local tables (`LoginAttemptRecorder`), not emitted as events. | Medium | `Identity/.../service/LoginAttemptRecorder.java`; no outbox table in Identity migrations | X1 + Audit service |
| I3 | **Permission catalogue is incomplete for maker-checker and audit.** V5 defines only `ACCOUNT_VIEW`, `ACCOUNT_CREATE`, `ACCOUNT_STATUS_CHANGE`. No approve/reject permission, and no audit-read permission. | Medium | `Identity/.../db/migration/V4__customer_permissions.sql`, `V5__account_permissions.sql` | Approval service (ADR-005), Audit service |
| I4 | **Rate limiting on auth endpoints** (see X3). Lockout exists after repeated failures, but there is no per-IP throttle on `/auth/login`, `/auth/refresh`, `/auth/mfa/verify`. | High | `IdentityApiIntegrationTest` covers lockout only | X3 |
| I5 | **Dev seed lives in two places.** `R__dev_seed_passwords.sql` is now on the classpath (`Identity/src/main/resources/db/migration-dev`) and mirrored in `infra/db/migration/identity/migration-dev`. They can drift. | Low | Both paths exist | Single source: keep service copy canonical, or generate the infra copy in CI |
| I6 | **Integration test uses hard-coded role id `[1]`** for the "teller cannot create users" case. Fragile if seed ids change. | Low | `IdentityApiIntegrationTest.tellerCannotCreateUsers` | Look the role id up by name like the admin test does |

---

## 3. Customer

| # | Gap | Severity | Evidence | Unblocked by |
|---|-----|----------|----------|--------------|
| C1 | **Dev seed not on the classpath.** The test profile loads `classpath:db/migration-dev`, but the service has only `db/migration` (V1). The branch seed exists only under `infra/db/migration/customer/migration-dev`. Tests or dev flows that need branches `HQ`, `BR001`, `BR002` will fail the same way Identity's did. | High | `Customer/src/test/resources/application-test.yml` line 6; `Customer/src/main/resources/db` has `migration/` only; `infra/db/migration/customer/migration-dev/R__dev_seed_branches.sql` | Copy the script to `Customer/src/main/resources/db/migration-dev` and add `locations` to `customer-service-dev.yml` |
| C2 | **Seed has branches only**, no sample customers or KYC records for manual/Postman testing. | Low | `R__dev_seed_branches.sql` | Add dev-only sample data |
| C3 | **Outbox not published** (X1). | High | `Customer/README.md` ("No publisher yet") | X1 |
| C4 | **KYC review is not maker-checker.** Reviews apply directly; ADR-005 routing through Approval is not wired. | Medium | `Customer/.../service/implementation/KycServiceImpl.java`; `docs/adr/ADR-005-*` | Approval service |
| C5 | **No audit trail beyond local state.** Changes are not forwarded to a central, append-only audit store. | Medium | No Audit integration; `infra/db/migration/audit` schema unused | X1 + Audit service |
| C6 | **No customer -> accounts view.** Account holders can be looked up only by calling Account with `customerId`; Customer does not aggregate. | Low | No Customer endpoint for accounts; Account search filters | Decide: Account `customerId` filter (already) vs. a BFF/aggregate endpoint in the frontend |
| C7 | **Statement-count / performance tests.** Account asserts query counts; Customer has no equivalent N+1 guard. | Low | Account `AccountApiIntegrationTest` vs. `CustomerApiIntegrationTest` | Add query-count assertions to list/search tests |

---

## 4. Account

| # | Gap | Severity | Evidence | Unblocked by |
|---|-----|----------|----------|--------------|
| A1 | **Balances are not driven by a ledger.** The Ledger service now writes `ledger.balance-updated` outbox events (payload `ApplyBalanceSnapshotRequest`), but no relay publishes them and Account has no consumer yet. | High | `Account/README.md`; `CommonsDTO/.../request/ApplyBalanceSnapshotRequest.java` | Ledger service + X2 contract |
| A2 | **Status change bypasses maker-checker.** Freeze/unfreeze/close apply immediately; the approval seam in `changeStatus` is unwired. | High | `Account/.../service/implementation/AccountServiceImpl.java` (seam comment near `changeStatus`); `docs/adr/ADR-005-*` | Approval service + `ACCOUNT_APPROVE` permission (I3) |
| A3 | **Customer and branch are trusted from the request.** Account does not verify the customer exists, is active/KYC-verified, or belongs to the branch. | Medium | `Account/README.md`; seam comment in `AccountServiceImpl` | Customer lookup API/client (with circuit breaker) or event-fed local read model |
| A4 | **Outbox not published** (X1). | High | `Account/README.md` ("no publisher yet") | X1 |
| A5 | **Business features absent:** holds distinct from `FROZEN`, per-account limits (daily/txn), joint holders. Schema has one `customer_id` and a status only. | Medium | `Account/.../db/migration/V1__init_schema.sql` | Product decisions + Transaction service for limits |
| A6 | **No dev seed** for accounts; only products are seeded (V2). | Low | `V2__seed_products.sql`; no `migration-dev` | Add dev-only sample accounts tied to seeded customers (C2) |
| A7 | **No `ci` / prod profile files.** CI sets `SPRING_PROFILES_ACTIVE=ci`, but there is no `application-ci.yml`; behaviour depends on Config Server being disabled via test config. | Low | `.github/workflows/ci.yml`; `Account/src/main/resources` has only `application.yml` | Confirm the `ci` profile is intentional; add a profile file or drop the variable |
| A8 | **Not in docker-compose or per-service CD** (X5, X6). | Medium | `Backend/docker-compose.yml` | X5, X6 |
| A9 | **Docs not verified against latest endpoints.** `docs/api-documentation.md` has structure but no per-endpoint reference for Account; Postman collection may lag. | Medium | `docs/api-documentation.md`, `docs/postman/` | Regenerate from `/v3/api-docs` and re-run the collection |
| A10 | **Missing test scenarios:** applying a stale/duplicate balance snapshot (version conflict), outbox row contents per event, approval path. | Medium | `Account/src/test/java/...` | Add with A1/A2 |

---

## 5. Services still to build, and what each unblocks

Only database migrations exist for these under `Backend/infra/db/migration/`; no code, no CI entry.

| Service | Closes these gaps |
|---------|-------------------|
| **Approval** | A2, C4, I3 (approve permission), maker-checker across all three services |
| **Ledger** | A1 (balance snapshots), reconciliation |
| **Transaction** | X4 (idempotency in practice), A5 (limits), money-movement APIs |
| **Notification** | I1 (MFA delivery), customer alerts from outbox events |
| **Audit** | I2, C5, append-only trail consuming X1 events |
| **Reporting** | Exports / operational reports (low priority) |

When building each: add it to `ci.yml`, `docker-compose.yml`, the Gateway routes (`Banking-System-Application-config/gateway`),
`ports.txt`, and the Postman folder.

---

## 6. Suggested order

1. **Pipeline fixes (X5, X6, X12, C1):** per-service Dockerfile or aggregator pom, Customer dev-seed on classpath, compose services including Account.
2. **Eventing foundation (X1, X2):** Kafka dependency, outbox relay, event contract in `docs/contracts.md`.
3. **Edge hardening (X3, I4, X9, X8):** rate limiting, security headers, log redaction.
4. **Observability and tests (X7, X11, A10, C7).**
5. **As services land:** Notification (I1) -> Approval (A2, C4, I3) -> Ledger (A1) -> Audit (I2, C5) -> Transaction (X4, A5).
6. **Reconcile docs:** A9, plus mark LLD sections for unbuilt services as "planned".

---

## 7. Summary

| Area | High | Medium | Low |
|------|------|--------|-----|
| Cross-cutting | 5 | 5 | 2 |
| Identity | 2 | 2 | 2 |
| Customer | 2 | 2 | 3 |
| Account | 3 | 5 | 2 |
