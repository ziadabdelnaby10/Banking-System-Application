# ADR-006 Implementation Guide — Outbox Eventing Strategy

Audience: Junior Java / Spring Boot developer

Goal: Implement the outbox pattern so domain changes are persisted and events published reliably, following `ADR-006-outbox-eventing.md`.

Prerequisites
- `outbox_events` table exists (see earlier migration)
- Kafka or a message broker available for production (optional for Phase B)

Steps
1. Read the ADR: `Backend/docs/adr/ADR-006-outbox-eventing.md`.
2. Create an `OutboxEvent` JPA entity mapped to `outbox_events` table.
3. Transactional persistence pattern:
   - Within the same database transaction where you change domain state, also insert an `outbox_event` row describing the event payload and headers. Do not publish to Kafka within the DB transaction.
4. Publisher component:
   - Implement a separate `OutboxPublisher` that queries `outbox_events` with status `NEW` and attempts to publish them to the broker.
   - On successful publish, update the `outbox_events` row to `PUBLISHED` with `published_at` timestamp.
   - On failure, increment `retry_count` and update `status` to `FAILED` if retries exceed threshold.
5. Idempotency and deduplication:
   - Ensure downstream consumers deduplicate messages by `aggregate_id` + `event_type` + `id` or the publisher attaches idempotency keys in headers.
6. Scheduling and scaling:
   - Run `OutboxPublisher` as a scheduled job or a dedicated worker thread/process.
   - If high throughput is required, implement a leasing mechanism so multiple publisher instances can coordinate (select for update skip locked).
7. Testing:
   - Unit tests for insertion of outbox rows within a transaction.
   - Integration tests using Testcontainers Kafka (or a broker simulator) to assert events are published and DB rows updated.
8. Operational notes:
   - Add metrics: `outbox_events_published_total`, `outbox_events_failed_total`, `outbox_event_publish_latency_seconds`.
   - Monitor `outbox_events` table growth and add retention/cleanup jobs for published events older than retention window.

PR checklist
- Outbox insert occurs inside domain transactions.
- Publisher publishes and transitions rows from `NEW` → `PUBLISHED` reliably.
- Retries and failure handling are covered by tests.
