# ADR-007 Implementation Guide — Notification Service (Outbox-first)

Audience: Junior Java / Spring Boot developer

Goal: Implement a `notification-service` that persists notifications and delivers emails reliably, following `ADR-007-notification-service.md`.

Prerequisites
- `notification` migration exists under `infra/db/migration/notification` (V1)
- MailHog available for local testing (we'll add it to `docker-compose`)

Steps
1. Read the ADR and plan: `Backend/docs/adr/ADR-007-notification-service.md` and `Backend/docs/notification/plan.md`.
2. Scaffold service module:
   - Create `services/notification-service` with a Spring Boot starter project (use `spring initializr` or copy an existing module). Include Flyway, Data JPA, Web, Security (if needed), and Mail/Redis dependencies.
3. Flyway migration:
   - Verify `V1__init_schema.sql` is discoverable by Flyway (classpath or `infra/db/migration` mapping). Add a quick smoke test to ensure tables exist.
4. Persistence layer:
   - Create JPA entities: `NotificationTemplate`, `Notification`, `NotificationAttempt` mapping to the new tables.
   - Create `NotificationRepository` and `NotificationAttemptRepository` interfaces.
5. Outbox consumer / input:
   - Implement a configurable input mode: default `outbox`.
   - If `outbox`, implement a poller that queries `outbox_events` for `notification.send` or a specific aggregate and creates `Notification` rows.
6. Send API:
   - Add REST endpoint `POST /api/v1/notifications/send` that accepts `externalId`, `to`, `templateCode`, `subject`, `body`, `metadata`.
   - On receive, persist a `Notification` row with status `PENDING` and return `202 Accepted`.
7. Delivery worker:
   - Implement a scheduled worker `NotificationDeliveryWorker` that picks `PENDING` notifications and attempts delivery using the configured provider.
   - Record each attempt in `notification_attempts` and update `notifications` status to `SENT` or `FAILED` accordingly.
   - Implement retry policy with exponential backoff and `notification.retry.maxAttempts` configured.
8. SMTP adapter and MailHog:
   - Implement an `SmtpNotificationSender` using `JavaMailSender` and configuration properties.
   - Add MailHog to `Backend/docker-compose.yml` for dev; example service:
```yaml
  mailhog:
    image: mailhog/mailhog
    ports:
      - "1025:1025" # SMTP
      - "8025:8025" # UI
```
9. Tests:
   - Unit tests for template rendering and the delivery worker behaviour.
   - Integration tests using Testcontainers for Postgres and MailHog to verify end-to-end: POST /send → worker sends email → notification_attempt recorded.
10. Observability:
   - Add Micrometer metrics: `notifications_created_total`, `notifications_sent_total`, `notifications_failed_total`.
   - Propagate `correlation_id` from incoming events to attempts and audit logs.
11. Security:
   - Protect template management endpoints with `ROLE_NOTIFICATION_ADMIN`.
   - Limit who can call the send API (service-to-service credentials or ACL).
12. CI/CD:
   - Add a `Dockerfile` in the service module and a CI job to build and run unit/integration tests.

PR checklist
- Service module scaffolded and builds.
- MailHog added to compose for local dev.
- Send API persists `Notification` and worker attempts delivery (integration test passes).
