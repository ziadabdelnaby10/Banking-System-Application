# ADR-001: Microservices-First Architecture

Status: Accepted

## Context

The banking platform must support strong consistency, auditability, strict domain boundaries, and high operational scale. The team needs a design that can scale horizontally, isolate failure domains, and support independent deployment of business capabilities without creating excessive coordination overhead.

## Decision

Implement the backend as a microservices-based system with domain-aligned service boundaries such as identity, customer, account, transaction, ledger, approval, audit, notification, and reporting. Use a gateway for routing, asynchronous messaging for integration, and independent databases per service to preserve autonomy and resilience.

## Consequences

- Business capabilities can evolve independently with separate deployment and scaling.
- Data consistency is managed through explicit contracts, outbox events, and saga/orchestration patterns rather than shared in-process transactions.
- Operational complexity increases because each service needs its own observability, deployment, and failure handling.
- Cross-service communication must be designed carefully around timeouts, retries, idempotency, and distributed tracing.
