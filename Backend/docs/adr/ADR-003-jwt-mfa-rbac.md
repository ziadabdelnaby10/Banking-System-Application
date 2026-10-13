# ADR-003: JWT, MFA, and RBAC Security Model

Status: Accepted

## Context

The platform must support secure browser-based access, privileged operations, and branch-scoped authorization.

## Decision

Use JWT access tokens with refresh tokens, mandatory MFA for privileged actions, and RBAC combined with branch-scoped functional permissions.

## Security controls

- JWT access tokens are short-lived and refresh tokens are rotated for session continuity.
- Authentication failures are persisted in a dedicated `login_attempts` audit table.
- Failed login attempts increment `users.failed_login_count` and trigger temporary lockout after a configured threshold.
- A user can remain locked until `users.locked_until` expires, at which point the counter resets and the account can be re-enabled by policy.
- Failed login events include correlation IDs and device metadata to support investigation and anomaly detection.
- Rate limiting and lockout checks are enforced at the API gateway and identity service boundary.

## Consequences

- Stateless API authentication remains practical for a separate SPA frontend.
- Privileged workflows can require a second verification step.
- Authorization logic must be enforced server-side and cannot rely on the UI.
