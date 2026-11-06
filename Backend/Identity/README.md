# Identity Service

Users, roles/permissions, authentication (password, optional MFA), refresh-token sessions and the login audit trail.
Issues RS256 access tokens; every other service verifies them with `CommonSecurity`.

## Run locally

```bash
# build shared libraries first
cd Backend/CommonsDTO && ./mvnw clean install
cd ../CommonSecurity && ./mvnw clean install
# database (docker-compose: bank_identity on localhost:5434), then
cd ../Identity && ./mvnw spring-boot:run          # profile "dev"
```

The `dev` profile seeds users through Flyway (`db/migration` + dev-only `db/migration-dev`), uses an **ephemeral**
signing key and prints MFA codes to the log. Seeded users (password `Dev@Passw0rd!2026`):

| user | MFA | notes |
|---|---|---|
| teller1 | no | simplest for smoke tests |
| admin, manager1, auditor1 | yes | login returns `MFA_REQUIRED`; the code is logged `[DEV ONLY]` |

Outside dev, `bank.identity.token.private-key-location` (PKCS#8 RSA PEM, >= 2048 bits) is mandatory:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out identity-jwt-private.pem
openssl pkey -in identity-jwt-private.pem -pubout -out identity-jwt-public.pem   # give this to other services
# env: BANK_IDENTITY_TOKEN_PRIVATEKEYLOCATION=file:/run/secrets/identity-jwt-private.pem
```

### Disabling security for local testing

Set `SECURITY_ENABLED=false` (or `--bank.security.enabled=false`) to call every endpoint without a token.
Every caller is then the built-in local `ADMIN` (`local-admin`, branch `LOCAL`); `@PreAuthorize` checks are
not applied. This user is not a row in `users`, so `GET /api/v1/users/me` returns 404 in this mode.
The service refuses to start with security disabled unless the `dev`, `local` or `test` profile is active
(see `SecurityDisabledConfiguration`). A loud warning is logged at startup.

## API (`/api/v1`)

| Method & path | Permission | Purpose |
|---|---|---|
| POST `/auth/login` | public | credentials -> tokens, or MFA challenge |
| POST `/auth/mfa/verify` | public | challenge token + code -> tokens |
| POST `/auth/refresh` | public | rotates the refresh token (reuse revokes the session) |
| POST `/auth/logout` | public | revokes the session |
| GET `/.well-known/jwks.json` | public | public signing key |
| GET `/users/me`, POST `/users/me/password` | authenticated | self service |
| POST `/users` | `USER_CREATE` + branch scope | create user |
| GET `/users`, GET `/users/{id}` | `USER_VIEW` (branch scoped) | search / read |
| PATCH `/users/{id}` | `USER_UPDATE` | e-mail, branch, MFA flag |
| PATCH `/admin/users/{id}/status` | `USER_LOCK` | ACTIVE / LOCKED / DISABLED |
| PUT, DELETE `/admin/users/{id}/roles/{roleId}` | `ROLE_ASSIGN` | role assignment |
| GET `/admin/roles` | `USER_VIEW` | role catalogue |
| GET `/admin/login-attempts` | `AUDIT_VIEW` | login audit |

Errors use the shared `ApiErrorDetail` payload (see `docs/contracts.md`).

## Behaviour worth knowing

- Failed logins lock the account after `bank.identity.lockout.max-failed-attempts` for `lock-duration`
  (the status stays ACTIVE; the lock simply expires). Unknown user and wrong password return the same answer.
- Refresh tokens are opaque (`<id>.<secret>`, only a SHA-256 hash is stored) and rotate on each use.
- Changing a password, MFA setting, role set or status revokes all sessions of the user.
- Usernames and e-mails are stored trimmed and lower-case.
- Role changes are applied directly; routing them through maker-checker (ADR-005) plugs in at `UserAccessPolicy`.
- Production MFA delivery needs the Notification service (ADR-007); until then delivery fails closed outside dev/test.

## Tests

`./mvnw test` runs unit tests; `IdentityApiIntegrationTest` uses Testcontainers/PostgreSQL and is skipped when Docker is not running.
