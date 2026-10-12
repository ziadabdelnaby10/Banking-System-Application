create table if not exists audit_logs (
    id bigserial primary key,
    actor_id bigint,
    actor_type varchar(50),
    action varchar(120) not null,
    entity_type varchar(80) not null,
    entity_id bigint,
    payload_json jsonb not null default '{}'::jsonb,
    ip_address inet,
    correlation_id varchar(64) not null,
    source_service varchar(60) not null,
    created_at timestamptz not null default now()
);

create table if not exists event_store (
    id bigserial primary key,
    aggregate_type varchar(80) not null,
    aggregate_id varchar(80) not null,
    event_type varchar(120) not null,
    payload_json jsonb not null,
    occurred_at timestamptz not null default now(),
    correlation_id varchar(64)
);

create index if not exists audit_logs_entity_created_at_idx on audit_logs (entity_type, entity_id, created_at);
create index if not exists audit_logs_actor_created_at_idx on audit_logs (actor_id, created_at);
create index if not exists event_store_aggregate_idx on event_store (aggregate_type, aggregate_id, occurred_at);
