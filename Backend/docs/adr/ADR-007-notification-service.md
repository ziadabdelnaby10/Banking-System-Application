# ADR-007: Notification Service — Outbox-first Reliable Delivery

Status: Accepted

## Context

The platform needs a reliable notification capability to persist notifications and deliver emails (and later other channels) with retry, auditability, and an easy developer experience for local testing.

## Decision

Create a dedicated `notification-service` (Spring Boot, Java 21) that follows an outbox-first consumption model: services create outbox events (or write to an agreed notifications API), and the `notification-service` consumes those events, persists notification records, and performs delivery via pluggable adapters (SMTP provider, provider APIs such as SendGrid). For local development, use MailHog.

Key properties:
- Outbox-first integration ensures reliable delivery and at-least-once processing semantics.
- Persist notifications and delivery attempts in Postgres for auditing and reconciliation.
- Provide a REST API to support immediate sends and template management.

## Rationale

- Outbox pattern decouples producers from delivery and avoids lost messages on transient failures.
- Persisted attempts and notification records make failure investigation and compliance easier.

## Consequences

- Additional DB migrations and a new microservice to build and operate.
- Operational considerations: provider credentials, retry/backoff configuration, and PII handling must be defined and secured.
- Admin role and RBAC rules required for template management and viewing notification content.
