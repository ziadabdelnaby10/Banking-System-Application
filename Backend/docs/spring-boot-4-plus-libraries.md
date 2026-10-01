# Suggested Spring Boot 4+ Libraries

The list below is a practical backend starter set for the banking platform. It assumes a Jakarta-compatible Spring Boot 4+ baseline and keeps the stack aligned with the project brief.

## Core application

- `spring-boot-starter-web`
- `spring-boot-starter-validation`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-security`
- `spring-boot-starter-actuator`
- `spring-boot-starter-aop`

## API and documentation

- `springdoc-openapi-starter-webmvc-ui`
- `jackson-datatype-jsr310`
- `jackson-module-parameter-names`

## Database and migrations

- `org.postgresql:postgresql`
- `org.flywaydb:flyway-core`
- `org.flywaydb:flyway-database-postgresql`

## Security and sessions

- `spring-boot-starter-oauth2-resource-server`
- `spring-boot-starter-oauth2-client` if external identity integration is needed later
- `spring-boot-starter-data-redis`
- `com.github.ben-manes.caffeine:caffeine` for local hot-cache scenarios
- `com.bucket4j:bucket4j-core` for rate limiting if you prefer library-level throttling

## Messaging and async processing

- `spring-boot-starter-amqp` if RabbitMQ is chosen
- `spring-kafka` if Kafka is chosen

## Observability

- `spring-boot-starter-logging`
- `io.micrometer:micrometer-registry-prometheus`
- `io.micrometer:micrometer-tracing-bridge-otel`
- `io.opentelemetry:opentelemetry-exporter-otlp`
- `net.logstash.logback:logstash-logback-encoder`

## Mapping and utility

- `org.mapstruct:mapstruct`
- `org.projectlombok:lombok` only if your team explicitly wants it
- `org.apache.commons:commons-lang3`

## Testing

- `spring-boot-starter-test`
- `spring-security-test`
- `org.testcontainers:junit-jupiter`
- `org.testcontainers:postgresql`
- `org.testcontainers:redis`
- `org.testcontainers:kafka` if eventing is introduced

## Notes

- Prefer starters and Jakarta-compatible libraries only.
- Keep the dependency set minimal until the first use case requires more.
- If you decide to use Kafka or RabbitMQ, wire the choice to the outbox publisher rather than dual-running both.
