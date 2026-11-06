---
description: 'Project instructions for the banking system app: secure Spring Boot microservices, ledger-safe financial logic, maker-checker approval flows, and ERP-style frontend behavior.'
applyTo: 'Backend/**/*.java, Backend/**/*.yml, Backend/**/*.yaml, Backend/**/*.md, Frontend/**/*.{ts,tsx,js,jsx,md}, README.md'
---

# Banking System App Instructions

Use these rules when generating or modifying code in this repository.

## Architecture and scope

- Treat this as a banking platform with strict operational and compliance requirements.
- Keep service boundaries aligned to business domains such as identity, customer, account, transaction, ledger, approval, audit, reporting, and notification.
- Prefer domain-aligned packages and service responsibilities over cross-cutting feature dumping.
- Do not introduce direct database access across services. Use APIs and events for cross-service communication.
- Favor explicit contracts, versioned APIs, and clear ownership of business logic.

## Backend expectations

- Use Java 21 and Spring Boot 3.x patterns unless the codebase explicitly says otherwise.
- Prefer constructor injection and `private final` dependencies.
- Keep controllers thin, services focused, and repositories simple.
- Put domain rules in the service/domain layer rather than spreading validation logic across controllers.
- Use validation annotations for request inputs and business validation boundaries.
- Follow the layered structure of API -> application/service -> domain -> infrastructure.

## Financial correctness rules

- Treat ledger integrity as non-negotiable.
- Any balance mutation must have a corresponding ledger/posting entry.
- Use idempotency keys for payment and transfer APIs.
- Validate currency consistency, account status, daily limits, and risk controls before posting operations.
- Prefer explicit transaction boundaries and safe concurrency controls for account operations.
- Make maker-checker approval logic visible in flows for privileged or high-risk actions.

## Security and compliance

- Enforce RBAC, branch scoping, and function-level permissions.
- Treat MFA and privileged action approval as mandatory requirements for sensitive operations.
- Log security-relevant and financial-relevant events with immutable audit records.
- Propagate correlation IDs across requests and events.
- Do not expose sensitive data without permission checks and masking rules.
- Use secure configuration patterns: externalize secrets and profiles, avoid hard-coded credentials.

## Event-driven and reliability patterns

- Prefer outbox patterns for reliable event publication.
- Design async flows with clear event payloads, retry semantics, and observability metadata.
- Use resilient inter-service communication with timeouts, retries, and circuit breakers where appropriate.
- Keep consistency goals explicit: transactional within a service, orchestrated or saga-oriented across services.

## Frontend expectations

- Build the frontend as a classic ERP-style operational UI, not a modern consumer app.
- Prefer dense tables, filters, role-based navigation, approval inboxes, audit views, and operational dashboards.
- Use clear status badges and action flows for active, frozen, pending, and rejected states.
- Show audit metadata when appropriate: created by/at and updated by/at.
- Protect high-risk actions with confirmation modals and reason capture.
- Respect role-based access and route guards.

## Testing and verification

- Write unit tests for domain rules and validation logic.
- Add integration tests for database-backed flows and service interactions.
- Prefer Testcontainers for backend integration tests when database behavior matters.
- Validate that security and permission boundaries remain enforced.
- For API changes, check request/response contracts and error handling.
- Verify builds and tests before concluding work on a change.

## Documentation and clarity

- Keep comments focused on intent, risk, or non-obvious decisions.
- Prefer descriptive names over abbreviations when the domain is financial or operational.
- When making a large change, mention the files, services, and business flow impacted.
- Align new code with the repo’s design documents and architecture decisions instead of introducing ad hoc patterns.

## Output expectations

- Propose concrete, implementation-ready changes for this banking domain.
- Call out compliance, auditability, and ledger safety considerations when relevant.
- Prefer practical patterns already used in the project over generic examples.
- If a request could create financial or security risk, identify the risk and suggest the safer path.
