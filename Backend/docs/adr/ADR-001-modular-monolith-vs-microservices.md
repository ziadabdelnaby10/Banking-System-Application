# ADR-001: Modular Monolith vs Microservices

Status: Accepted

## Context

The banking platform must support strong consistency, auditability, and strict domain boundaries while remaining practical for a small or medium-sized implementation team.

## Decision

Implement the backend as a modular monolith first, organized by domain modules such as customer, account, transaction, ledger, approval, auth, audit, and reporting.

## Consequences

- Domain ownership stays clear without the operational overhead of distributed services.
- Cross-module calls remain in-process but must go through explicit service contracts.
- The codebase can later split into services if volume or team size requires it.
