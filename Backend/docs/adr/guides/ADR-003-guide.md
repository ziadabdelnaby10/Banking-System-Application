# ADR-003 Implementation Guide — JWT, MFA, RBAC Security Model

Audience: Junior Java / Spring Boot developer

Goal: Implement JWT access tokens, refresh tokens, MFA endpoints, and RBAC/branch-scoped permissions per `ADR-003-jwt-mfa-rbac.md`.

Prerequisites
- Familiarity with Spring Security
- `users`, `roles`, `role_permissions`, `user_roles`, and `mfa_challenges` tables present

Steps
1. Read the ADR: `Backend/docs/adr/ADR-003-jwt-mfa-rbac.md`.
2. Add dependencies in `pom.xml`:
   - `spring-boot-starter-security`
   - `spring-boot-starter-oauth2-resource-server` (for JWT processing)
   - `jjwt` or use Spring's Nimbus JWT support
3. Implement authentication flow:
   - Create `AuthController` with endpoints `/login` and `/token/refresh`.
   - On `/login`, validate username/password via `UserDetailsService` and `PasswordEncoder`.
   - If user has `mfa_enabled`, create entry in `mfa_challenges` and respond with `MFA_REQUIRED` status and challenge id.
4. Implement MFA verification endpoint:
   - POST `/mfa/verify` with `{challengeId, code}`.
   - Verify `code_hash` in `mfa_challenges`, mark verified, and issue JWT access and refresh tokens.
5. Implement JWT issuing and validation:
   - Create `JwtTokenService` to create access (short-lived) and refresh (longer-lived) tokens.
   - Add a `JwtAuthenticationFilter` or configure the resource server to validate tokens.
6. Implement RBAC and branch scoping:
   - Load `user_roles` and `role_permissions` when authenticating and include them in the `Authentication` principal.
   - Implement a method security layer (`@PreAuthorize`) to check permissions and branch scope.
   - For branch scoping, include `branchId` in the principal and validate in service layer.
7. Add rate limiting and lockout on auth endpoints:
   - Use Redis + bucket4j or simple Redis counters + lockout TTL.
8. Testing:
   - Unit tests for token creation/validation.
   - Integration tests that simulate login + MFA + token refresh.

PR checklist
- Secure endpoints configured with appropriate roles.
- `mfa_challenges` CRUD verified.
- Token expiry and refresh flows tested.
- Lockout / rate limit on auth endpoints demonstrated in tests.
