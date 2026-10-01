# ADR-003: JWT, MFA, and RBAC Security Model

Status: Accepted

## Context

The platform must support secure browser-based access, privileged operations, and branch-scoped authorization.

## Decision

Use JWT access tokens with refresh tokens, mandatory MFA for privileged actions, and RBAC combined with branch-scoped functional permissions.

## Consequences

- Stateless API authentication remains practical for a separate SPA frontend.
- Privileged workflows can require a second verification step.
- Authorization logic must be enforced server-side and cannot rely on the UI.
