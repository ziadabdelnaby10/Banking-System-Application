# CommonSecurity

Reusable servlet security for every Bank microservice: stateless JWT (RS256) resource server, permission-based RBAC,
branch scoping, correlation IDs and the shared 401/403 error payload. Artifact: `org.bank.common:security`.

## Use it in a service

1. Build once: `mvnw clean install` (after `CommonsDTO`).
2. Add the dependency:

```xml
<dependency>
    <groupId>org.bank.common</groupId>
    <artifactId>security</artifactId>
    <version>1.0.0</version>
</dependency>
```

3. Configure how to verify tokens issued by Identity (one of the two; the public key is preferred):

```yaml
bank:
  security:
    jwt:
      issuer: http://identity-service            # must equal Identity's issuer
      public-key-location: file:/run/secrets/identity-jwt-public.pem   # X.509 PEM, RSA >= 2048
      # jwk-set-uri: http://identity-service/.well-known/jwks.json     # alternative: rotating keys
    public-paths: /actuator/health/**,/actuator/info                   # everything else needs a token
    branch-bypass-roles: ADMIN,AUDITOR
```

That is all: the auto-configuration registers the security filter chain, the JWT decoder, method security,
the correlation-ID filter and the JSON error handlers. Every bean is `@ConditionalOnMissingBean`, so a service
can override any piece. Set `bank.security.enabled=false` to switch it off (tests only).

## In your code

```java
@PreAuthorize("hasAuthority('USER_VIEW')")                           // permission from the token
@PreAuthorize("hasRole('ADMIN')")                                    // role
@PreAuthorize("@branchAccess.canAccess(#branchCode)")                // branch scoping
public AccountResponse get(@AuthenticationPrincipal AuthenticatedUser caller, String branchCode) { ... }

Optional<String> branch = branchAccess.restrictedBranch();           // filter queries for branch-restricted callers
AuthenticatedUser caller = CurrentUser.require();                    // from services
```

Roles are exposed as `ROLE_<NAME>` authorities, permissions as plain authorities (see `Permissions`).

## Token contract (claims)

`sub` = user id (UUID), `preferred_username`, `roles`, `permissions`, `branch`, `mfa` (second factor completed),
`sid` (session id), plus `iss`/`iat`/`exp`. Tokens without a valid `sub` or issuer are rejected with 401 `INVALID_TOKEN`.

## Scope

Servlet (Spring MVC) only. Reactive/Gateway support is intentionally not included.
