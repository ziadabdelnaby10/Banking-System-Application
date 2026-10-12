# ADR-002 Implementation Guide — PostgreSQL + Redis

Audience: Junior Java / Spring Boot developer

Goal: Configure PostgreSQL as the system of record and Redis for caching and rate-limiting as described in `ADR-002-postgresql-redis.md`.

Prerequisites
- `docker-compose.yml` available in `Backend` (postgres + redis services present)
- Spring Boot familiarity and `application.yml`/`application.properties` editing

Steps
1. Read the ADR file: open `Backend/docs/adr/ADR-002-postgresql-redis.md`.
2. Add DB config to Spring Boot:
   - In `src/main/resources/application.yml` (or create `application-dev.yml`), add `spring.datasource.url`, `username`, `password` and Flyway properties.
   - Example:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/banking_app
    username: banking_app
    password: banking_app_password
  flyway:
    enabled: true
    locations: classpath:db/migration
```
3. Add Redis configuration:
   - Add `spring.redis.host` and `port` and configure a `RedisTemplate<String, Object>` bean if needed.
4. Add dependencies in `pom.xml`:
   - `org.postgresql:postgresql`
   - `org.flywaydb:flyway-core`
   - `org.springframework.boot:spring-boot-starter-data-redis`
5. Wire Testcontainers for integration tests:
   - Add `org.testcontainers:postgresql` and `org.testcontainers:redis` to `test` scope and write an integration test that starts both containers and verifies the `DataSource` and `RedisTemplate` beans.
6. Validate Docker Compose:
   - Start `docker-compose up -d postgres redis` and verify connectivity.
7. Add a simple repository and integration test:
   - Create a JPA entity (e.g., `Branch`) and `BranchRepository` and a test that persists and reads it.

PR checklist
- `application.yml` entries added for Postgres/Redis.
- Dependencies added in `pom.xml`.
- Integration test using Testcontainers passes.
- `docker-compose` services start cleanly.
