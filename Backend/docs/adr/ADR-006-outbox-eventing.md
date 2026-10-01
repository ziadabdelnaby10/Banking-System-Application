# ADR-006: Outbox Eventing Strategy

Status: Accepted

## Context

The system needs a reliable way to publish domain events without losing messages when a transaction commits.

## Decision

Use the outbox pattern to persist events in the same database transaction as the business change, then publish them asynchronously.

## Consequences

- Event publication becomes resilient to partial failures.
- Future Kafka or RabbitMQ integration can consume the outbox table.
- A background publisher or scheduler will be required.
