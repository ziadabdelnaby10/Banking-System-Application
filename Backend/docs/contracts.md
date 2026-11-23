# Backend Contract Standards

## Ownership
- Owner: Platform Contracts Team (single team)
- Module: `Backend/CommonsDTO`
- Artifact: `org.bank.common:dto`

## Scope
- Shared DTOs for synchronous APIs and async message payloads
- Shared error contracts (Problem-style response + field errors)
- Shared enums used across bounded contexts
- Bean validation annotations for contract-level constraints

## Exclusions
- No service business logic
- No persistence entities
- No Spring Boot runtime or application configuration

## Versioning
- Follow semantic versioning
- `MAJOR`: breaking field rename/removal/type change
- `MINOR`: additive backward-compatible fields/types
- `PATCH`: docs and non-contract internal fixes

## Account Contracts (since 1.1.0)
- Enums: `AccountStatus` (with `canTransitionTo`), `AccountType`
- Requests: `OpenAccountRequest`, `ChangeAccountStatusRequest` (mandatory `reason`), `ApplyBalanceSnapshotRequest` (Ledger to Account)
- Responses: `AccountResponse`, `AccountSummaryResponse`, `AccountBalanceResponse`, `AccountProductResponse`, `AccountStatusHistoryResponse`
- Money is `BigDecimal` with scale 2; currency is an uppercase ISO-4217 code

## Error Contract
- Canonical payload: `ApiErrorDetail` (`timestamp`, `status`, `error`, `code`, `message`, `path`, `correlationId`, `violations[]`)
- Field level validation: `violations` list of `FieldViolation(field, message)`
- Stable functional error code: `ErrorCode` enum, which also owns the HTTP status (`ErrorCode.httpStatus()`)
- Throw `BankException` subclasses (`ResourceNotFoundException`, `DuplicateResourceException`, `BusinessRuleException`, `AuthenticationFailedException`, `AccountLockedException`) from services; each service maps them in one `@RestControllerAdvice`
- Security failures (401/403) are written by `Backend/CommonSecurity` in the same shape

| ErrorCode | HTTP |
|---|---|
| BAD_REQUEST | 400 |
| UNAUTHENTICATED, INVALID_CREDENTIALS, INVALID_TOKEN, MFA_INVALID | 401 |
| ACCESS_DENIED, ACCOUNT_DISABLED | 403 |
| RESOURCE_NOT_FOUND | 404 |
| DUPLICATE_RESOURCE | 409 |
| VALIDATION_FAILED, BUSINESS_RULE_VIOLATION | 422 |
| ACCOUNT_LOCKED | 423 (`Retry-After` header when known) |
| INTERNAL_ERROR | 500 |

## Security Library
- Module: `Backend/CommonSecurity`, artifact `org.bank.common:security` (see its README)
- Depends on `org.bank.common:dto`; DTOs stay free of Spring Security

## Local Publishing (Option A)
Build order matters: `CommonsDTO` first, then `CommonSecurity`, then services.
```bash
cd Backend\CommonsDTO && mvnw.cmd clean install
cd Backend\CommonSecurity && mvnw.cmd clean install
```

## Consumer Workflow
1. Publish contracts locally (`mvnw.cmd clean install`)
2. Add dependency to service `pom.xml`
3. Use explicit version pinning in each service
4. Run service tests after contract updates

