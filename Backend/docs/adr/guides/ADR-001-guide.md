# ADR-001 Implementation Guide — Modular Monolith vs Microservices

Audience: Junior Java / Spring Boot developer

Goal: Implement the modular-monolith structure and enforce domain boundaries described in `ADR-001-modular-monolith-vs-microservices.md`.

Prerequisites
- Java 21, Maven/Gradle project in `Backend` root
- Basic familiarity with Spring Boot, packages, and modules

Steps
1. Read the ADR: open `Backend/docs/adr/ADR-001-modular-monolith-vs-microservices.md` and understand the rationale.
2. Create module packages: under `src/main/java` (or the multi-module structure), create domain packages: `customer`, `account`, `transaction`, `ledger`, `approval`, `auth`, `audit`, `reporting`.
   - Example: `com.yourorg.bank.customer`, `com.yourorg.bank.account`.
3. Add a module README: create `Backend/modules/README.md` or a per-module README explaining the purpose and public service interfaces.
4. Define service interfaces for cross-module calls: create interface classes in `application` or `api` packages, e.g. `CustomerServiceApi` in `customer` that exposes only DTOs.
   - Keep domain entities in each module package and expose DTOs across boundaries.
5. Enforce boundaries locally:
   - Add a package-info.java with a short comment in each module describing allowed dependencies.
   - Add a simple unit test that asserts classes from one module do not import repository classes from another. Use reflection to scan for forbidden package imports.
6. Configure the build:
   - If using a single Spring Boot project, keep modules as Java packages. If preferred, create a multi-module Maven setup with module POMs for each domain.
   - Ensure module artifacts are built as part of the root build.
7. Add CI checks:
   - Add a lightweight static-check in your CI job that runs a small script to detect forbidden package imports or enforce architectural rules.
8. Document the contract style:
   - Add `Backend/docs/contracts.md` with DTO naming, error handling conventions (Problem+JSON), correlation-id propagation rules.
9. PR checklist for this ADR:
   - Module package directories created.
   - One sample API interface added for a cross-module call.
   - Unit test proving package boundary.
   - `modules/README.md` added.

How to verify
- Build the project: run `./mvnw -B -ntp -DskipTests package`.
- Run the boundary tests. They should fail if any forbidden imports exist.
