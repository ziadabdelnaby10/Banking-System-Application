-- V1: Notification service initial schema
-- Notifications, templates, and delivery attempts

create table if not exists notification_templates (
    id bigserial primary key,
    code varchar(120) not null unique,
    channel varchar(20) not null default 'EMAIL',
    subject_template varchar(512),
    body_template text,
    description text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists notifications (
    id bigserial primary key,
    external_id varchar(128), -- optional idempotency/external reference
    template_id bigint references notification_templates(id),
    channel varchar(20) not null default 'EMAIL',
    recipient varchar(512) not null,
    subject varchar(512),
    body text,
    metadata jsonb default '{}'::jsonb,
    status varchar(20) not null default 'PENDING', -- PENDING, SENT, FAILED
    attempts integer not null default 0,
    last_attempt_at timestamptz,
    created_at timestamptz not null default now(),
    sent_at timestamptz,
    created_by bigint references users(id)
);

create index if not exists notifications_external_idx on notifications (external_id);
create index if not exists notifications_status_created_idx on notifications (status, created_at);

create table if not exists notification_attempts (
    id bigserial primary key,
    notification_id bigint not null references notifications(id) on delete cascade,
    provider varchar(120) not null,
    status varchar(20) not null,
    response_text text,
    attempt_at timestamptz not null default now()
);

create index if not exists notification_attempts_notification_idx on notification_attempts (notification_id);

-- Simple trigger to update updated_at on templates
create or replace function set_updated_at_notifications()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create trigger trg_notification_templates_updated_at before update on notification_templates
for each row execute function set_updated_at_notifications();
