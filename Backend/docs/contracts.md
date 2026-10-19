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

## Error Contract
- Canonical payload: `ProblemDetails`
- Field level validation: `ApiErrorDetail` list
- Stable functional error code: `ErrorCode` enum

## Local Publishing (Option A)
```bash
cd Backend\CommonsDTO
mvnw.cmd clean install
```

## Consumer Workflow
1. Publish contracts locally (`mvnw.cmd clean install`)
2. Add dependency to service `pom.xml`
3. Use explicit version pinning in each service
4. Run service tests after contract updates

