**Outcome:** Detailed plan for a **Core Banking + ERP-lite** app (Spring Boot microservices + separate SPA frontend), including AI instructions, use cases, user stories, DB schema, and app flow.

## 1) Problem & Approach

Build a banking operations platform with a classic ERP-style UI, supporting high operational scale (target: ~100K users), strong governance (RBAC, 2FA, maker-checker), and full auditability.  
Architecture: **microservices-first** (Spring Boot services behind an API gateway), with domain-aligned service boundaries, Kafka/RabbitMQ for async events, outbox-based reliability, and a SPA frontend consuming secured REST APIs.

---

## 2) Detailed AI Instructions

### Backend AI Instructions (Spring Boot Microservices)

**Role:** You are a senior backend engineer building a secure banking core as a distributed system.

**Tech Stack**
1. Java 21, Spring Boot 3.x
2. Spring Web, Spring Security, Spring Data JPA, Validation
3. PostgreSQL per service (or per bounded context), Redis for cache/rate limiting
4. Kafka/RabbitMQ for event-driven integration
5. Flyway for DB migrations per service
6. API Gateway (Spring Cloud Gateway or Kong/NGINX)
7. Service discovery / config (Spring Cloud / Consul / Eureka / Kubernetes native)
8. OpenAPI/Swagger
9. JUnit + Testcontainers

**Architecture Rules**
1. Use **microservices** by bounded context: `gateway`, `identity`, `customer`, `account`, `transaction`, `ledger`, `approval`, `notification`, `reporting`, `audit`.
2. Each service owns its database schema and business logic; do not read/write another service's database directly.
3. Prefer explicit contracts via REST, async events, and message contracts.
4. Use **hexagonal-ish layering** inside each service:
   - API (controllers, DTOs)
   - Application services (use-cases)
   - Domain (entities, rules)
   - Infrastructure (repositories, messaging, integrations)

**Security & Compliance**
1. JWT access + refresh tokens issued by `identity` and validated at the gateway and service boundaries.
2. Mandatory 2FA for privileged actions.
3. RBAC + branch scoping + functional permissions enforced in each service and validated via claims/authorization metadata.
4. Maker-checker workflow for critical operations (large transfer, account freeze, role change).
5. Immutable audit log for all CRUD + approvals + auth events.
6. Data encryption:
   - In transit: TLS between clients, gateway, services, and brokers
   - At rest: DB-level encryption for sensitive fields (PII/account identifiers where needed)
7. Add rate limits and lockout policy on auth endpoints.

**Domain Behavior**
1. Double-entry ledger required for financial posting.
2. No balance mutation without corresponding ledger posting.
3. Idempotency key on transfer/payment APIs.
4. Strong validation: currency consistency, account status checks, daily limits, AML flags hook.
5. Use DB transactions within a service; for cross-service workflows use orchestration/Saga patterns and outbox events.

**Reliability & Performance**
1. P95 API latency target for common reads < 300ms (local/dev baseline friendly).
2. Use optimistic locking for concurrent account operations within a service.
3. Add outbox pattern for publishing domain events.
4. Cache hot reads (customer/account snapshots) with safe invalidation.
5. Use circuit breakers, retries, and timeouts between services.

**API Standards**
1. Versioned REST APIs: `/api/v1/...`
2. Problem+JSON error responses with machine-readable codes.
3. Pagination/filter/sort for list endpoints.
4. Correlation ID propagated in logs and response headers.
5. All service-to-service communication should be resilient and traceable with observability metadata.

**Testing**
1. Unit tests for domain rules.
2. Integration tests with Testcontainers for DB-backed use-cases.
3. Security tests for role/permission boundaries.
4. Contract tests for critical API responses.
5. Event-driven integration tests for outbox and message handling.

---

### Frontend AI Instructions (Separate SPA, Classic ERP UI)

**Role:** You are a senior frontend engineer building a reliable, classic banking ERP interface.

**Tech Stack**
1. React + TypeScript (or Angular equivalent)
2. State: Redux Toolkit / NgRx (predictable state)
3. UI: classic enterprise component library (table-heavy, form-heavy layout)
4. Routing with role guards
5. i18n-ready strings
6. E2E: Playwright/Cypress

**UX/Design Rules**
1. Classic ERP layout:
   - Left navigation (modules)
   - Top bar (branch/user/context/actions)
   - Dense data grids + advanced filters
2. Prioritize operational clarity over visual novelty.
3. Show status badges (active/frozen/pending-approval/rejected).
4. Always show audit metadata on records (created by/at, last updated).

**Security & Access**
1. Token handling with secure storage strategy.
2. Auto logout on inactivity.
3. 2FA step in login and high-risk actions.
4. Route guards + component-level permission checks.
5. Mask sensitive values by permission.

**Key Screens**
1. Dashboard (KPIs, pending approvals, alerts)
2. Customers
3. Accounts
4. Transfers/Payments
5. General Ledger postings
6. Approval inbox (maker-checker)
7. Audit trail explorer
8. User/Role admin
9. Reports export

**Interaction Rules**
1. Forms must support draft, validation, and server-side error mapping.
2. Risk operations require confirmation modal + reason note.
3. Bulk actions only for authorized roles with explicit confirmation.
4. Optimistic UI only for low-risk non-financial operations.

**Performance**
1. Virtualized tables for large datasets.
2. Debounced server-side filtering.
3. Request cancellation on rapid navigation.
4. Cache reference data (branches, products, roles).

**Quality**
1. Component tests for forms and permission rendering.
2. E2E paths: login+2FA, create transfer + approval, reject flow, audit lookup.
3. Accessibility: keyboard-first navigation and ARIA for core controls.

---

## 3) Use Cases

1. **Onboard Customer**: Create KYC profile, verify identity, assign branch.
2. **Open Account**: Create savings/current account for customer.
3. **Deposit/Withdraw**: Teller executes cash operations with limits.
4. **Internal Transfer**: Move funds between accounts with validations.
5. **Account Freeze/Unfreeze**: Compliance/admin action with approval.
6. **Maker-Checker Approval**: Reviewer approves/rejects pending critical ops.
7. **User & Role Management**: Admin grants scoped permissions.
8. **Audit Investigation**: Compliance searches immutable event logs.
9. **Ledger Reconciliation**: Finance reviews journal entries and balances.
10. **Reporting**: Export operational and financial reports.

---

## 4) User Stories (sample)

1. As a **Teller**, I want to post deposit/withdrawal so branch transactions are recorded accurately.
2. As a **Branch Manager**, I want to approve high-value transfers so risk controls are enforced.
3. As a **Compliance Officer**, I want full audit trails so I can investigate suspicious activity.
4. As an **Operations User**, I want ERP-style filtering and bulk search so I can process records quickly.
5. As an **Admin**, I want RBAC and branch-scoped permissions so users only access allowed features.
6. As a **Checker**, I want maker-checker inbox with diff/context so I can approve safely.
7. As a **Customer Service Agent**, I want account/customer 360 view so I can resolve requests fast.
8. As a **Security Admin**, I want enforced 2FA and login lock policy so unauthorized access is reduced.

---

## 5) Database Schema (High-Level)

Each service owns its own database schema, with data integration through event-driven contracts.

**Core Service Databases**
1. `identity-db`: `users`, `roles`, `permissions`, `sessions`, `mfa_challenges`
2. `customer-db`: `customers`, `branches`, `kyc_records`
3. `account-db`: `accounts`, `account_products`, `account_status_history`
4. `transaction-db`: `transactions`, `payment_requests`, `idempotency_keys`
5. `ledger-db`: `ledger_entries`, `balances`, `journal_batches`
6. `approval-db`: `approvals`, `approval_policies`, `approval_tasks`
7. `audit-db`: `audit_logs`, `event_store`
8. `reporting-db`: `report_jobs`, `aggregated_metrics`

**Shared Cross-Cutting Tables / Contracts**
1. `outbox_events` (in each producer service) for reliable event publication
2. `consumer_offsets` / CDC tracking for replay and event consumption
3. `correlation_id` propagated across all service calls and events

**Indexes/Constraints**
1. Unique: `customer_no`, `account_no`, `txn_ref`, `idempotency_key`
2. FK constraints on all relations within each service database
3. Indexes: `accounts(customer_id,status)`, `transactions(created_at,status)`, `approvals(status,created_at)`, `audit_logs(entity_type,entity_id,created_at)`

---

## 6) App Flow (End-to-End)

1. **Gateway / Identity Flow**
   - Client calls API Gateway → token validation and routing.
   - `identity` service issues/validates JWTs and MFA challenges.
2. **Operational Flow (Maker)**
   - `customer` and `account` services validate customer/account state.
   - `transaction` service creates a pending payment or transfer request.
   - If critical, it emits a `payment_requested` event and creates an approval task in `approval`.
3. **Approval Flow (Checker)**
   - `approval` service exposes inbox and decision APIs.
   - Checker reviews context, account impact, and risk metadata.
   - Approves/rejects with mandatory reason and writes to immutable audit stream.
4. **Posting Flow**
   - On approval, `transaction` service triggers ledger posting.
   - `ledger` service writes double-entry postings and updates balances.
   - `account` service consumes events and updates account state if needed.
   - Outbox event is emitted for downstream notifications/reporting.
5. **Audit Flow**
   - Every service writes immutable audit records and emits domain events.
   - `audit` service stores append-only event data for compliance and investigations.
6. **Reporting Flow**
   - `reporting` service aggregates ledger/transaction/audit events.
   - Users export CSV/PDF by permission via asynchronous jobs and status tracking.

---

## 7) Suggested Service Boundaries (Implementation Plan Structure)

1. Foundation: API gateway, service discovery, shared contracts, observability baseline
2. Identity & Access: JWT, MFA, RBAC, branch scoping, session management
3. Customer & Master Data: branches, customers, KYC, roles, permissions
4. Account Management: savings/current accounts, lifecycle, limits, freeze/close
5. Transaction & Payment Engine: transfer execution, idempotency, validation
6. Ledger & Posting: double-entry journal posting, balance reconciliation
7. Approval & Governance: maker-checker, risk review, policy enforcement
8. Notification & Messaging: email/SMS/customer alerts, webhook integration
9. Audit & Compliance: immutable audit logs, searchable event store
10. Reporting & Exports: operational/financial reporting and CSV/PDF jobs
11. Frontend ERP shell and business screens
12. Hardening: resilience, security tests, performance, tracing, chaos checks

---

## 8) ADRs to Create Early

1. ADR-001: Microservices-First Architecture
2. ADR-002: PostgreSQL + Redis choice
3. ADR-003: JWT + MFA + RBAC security model
4. ADR-004: Double-entry ledger design
5. ADR-005: Maker-checker workflow and approval boundaries
6. ADR-006: Outbox/eventing strategy for future scale

---

## 9) SQL Todo Backlog (reflected)

Use these todo IDs/titles/descriptions in your tracker:

1. `service-foundation` — Creating shared gateway, service skeletons, and contract standards  
2. `identity-access` — Implementing JWT, MFA, RBAC, and branch scoping  
3. `master-data` — Building branches, roles, permissions, and customer master data  
4. `customer-account` — Implementing customer onboarding and account lifecycle  
5. `transaction-ledger` — Building transfers/payments with double-entry posting  
6. `maker-checker` — Implementing approval workflows for critical operations  
7. `audit-compliance` — Building immutable audit logs and compliance queries  
8. `reporting-module` — Implementing operational/financial reporting exports  
9. `frontend-erp-shell` — Building SPA shell, navigation, and role-based routing  
10. `frontend-business-screens` — Implementing customer/account/transaction/approval/audit screens  
11. `integration-hardening` — End-to-end integration, security, and performance hardening  

**Dependency chain:**  
`service-foundation` → `identity-access` → (`master-data`, `frontend-erp-shell`) → `customer-account` → `transaction-ledger` → `maker-checker` → (`audit-compliance`, `frontend-business-screens`) → `reporting-module` → `integration-hardening`

---

## 10) Sequence Diagrams