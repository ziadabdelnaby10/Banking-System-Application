# API documentation (Swagger) and Postman

## Swagger UI (central page on the Gateway)

Start Config, Discovery, Identity and Gateway, then open **http://localhost:9000/** (redirects to `/swagger-ui.html`).
The top-right dropdown lists one entry per microservice (currently `identity-service`, `customer-service` and `account-service`). Each OpenAPI document is
loaded **through the Gateway** (`/docs/<service>/v3/api-docs`), and "Try it out" calls go through the Gateway too.

To call protected endpoints: run `POST /api/v1/auth/login` in the page, copy `tokens.accessToken`, click **Authorize**
and paste it (without `Bearer `).

### Adding another microservice

1. Add the `springdoc-openapi-starter-webmvc-api` dependency to the service (it serves `/v3/api-docs`).
2. Gateway routes: an API route plus a docs route (`Path=/docs/<name>/v3/api-docs/**`, `RewritePath=/docs/<name>/(?<segment>.*), /$\{segment}`).
3. Gateway `springdoc.swagger-ui.urls`: one more `{name, url: /docs/<name>/v3/api-docs}` entry.
4. Dev public paths already cover `/docs/*/v3/api-docs/**`.

### Exposure

- `dev` profile (default): documentation is public (no token) so the page works. Real APIs still need a token.
- `prod` profile: `application-prod.yml` in Gateway and Identity switches the documents and the UI off, and the Gateway
  does not list any Swagger path as public.

### Config Server note (important)

Routes and `bank.security.*` are also served by the Config Server (repo branch `config`). Remote values **replace** the
local YAML (lists are replaced as a whole), so the local files only take effect where the remote config does not define the
same key. If `/` or `/swagger-ui.html` answers 401, or the dropdown is empty, copy these into the Gateway's config in the
config repo (dev profile):

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            # ...keep the existing routes, add:
            - id: identity-docs
              uri: lb://identity-service
              predicates:
                - Path=/docs/identity/v3/api-docs/**
              filters:
                - RewritePath=/docs/identity/(?<segment>.*), /$\{segment}
            - id: root-to-swagger
              uri: no://op
              predicates:
                - Path=/
              filters:
                - RedirectTo=302, /swagger-ui.html
bank:
  security:
    public-paths:
      # ...keep the existing paths, add:
      - /
      - /swagger-ui.html
      - /swagger-ui/**
      - /webjars/**
      - /v3/api-docs/**
      - /docs/*/v3/api-docs/**
springdoc:
  enable-default-api-docs: false
  swagger-ui:
    urls:
      - name: identity-service
        url: /docs/identity/v3/api-docs
    urls-primary-name: identity-service
```

Identity needs no config-repo change: its document endpoint is made public in code (`OpenApiConfig`).

## Postman

Files in [postman/](postman/):

- `identity.postman_collection.json` - Auth, Users, Admin, JWKS (+ a "Manual only" folder). Test scripts save
  `accessToken`, `refreshToken`, `challengeToken`, `userId` and `roleId` automatically; every request after login sends the
  token as Bearer.
- `customer.postman_collection.json` - Branches, Customers, KYC. Reuses the `accessToken` saved by the Identity login and
  saves `customerId` / `kycRecordId`. KYC review must be run with a different user than the submitter (maker-checker).
- `account.postman_collection.json` - Products, Accounts (open, search, get, balance), Lifecycle (freeze, unfreeze, close,
  status history). Reuses `accessToken` and `customerId`; saves `accountId` / `accountNo`. Status changes need a MANAGER or
  ADMIN user (ACCOUNT_STATUS_CHANGE).
- `local.postman_environment.json` - `baseUrl` = `http://localhost:9000` (the Gateway), seed users and passwords.

Steps: Import the collections, select the **Banking System - Local (Gateway)** environment, run **Auth > 1. Login**, then use any
request. For `admin` / `manager1` / `auditor1` use **Auth > 2** then **3** (the MFA code is printed in the Identity log
in dev, `[DEV ONLY]`; put it into the `mfaCode` environment variable).

To regenerate a collection straight from the live spec instead (always up to date, no test scripts):
Postman > Import > Link > `http://localhost:9000/docs/identity/v3/api-docs`.
