# ADR-002: PostgreSQL + Redis

Status: Accepted

## Context

The system needs transactional integrity for banking operations and fast access to hot reference data and security controls.

## Decision

Use PostgreSQL as the system of record and Redis for caching, rate limiting, and session-adjacent workflows.

## Consequences

- Financial and approval data benefit from relational constraints and ACID transactions.
- Redis can accelerate reads without becoming a source of truth.
- Operational complexity stays low compared with introducing more specialized data stores too early.
