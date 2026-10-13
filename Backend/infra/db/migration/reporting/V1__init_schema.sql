create table if not exists report_jobs (
    id bigserial primary key,
    report_code varchar(80) not null,
    requested_by uuid not null,
    correlation_id varchar(64) not null,
    status varchar(20) not null default 'QUEUED',
    format varchar(20) not null default 'CSV',
    filters_json jsonb not null default '{}'::jsonb,
    file_uri text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    completed_at timestamptz,
    constraint report_jobs_status_chk check (status in ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

create table if not exists aggregated_metrics (
    id bigserial primary key,
    metric_key varchar(120) not null,
    metric_date date not null,
    metric_value numeric(19,4) not null,
    dimension_json jsonb not null default '{}'::jsonb,
    correlation_id varchar(64),
    created_at timestamptz not null default now()
);

create index if not exists report_jobs_requested_status_idx on report_jobs (requested_by, status, created_at);
create index if not exists report_jobs_correlation_idx on report_jobs (correlation_id);
create index if not exists aggregated_metrics_key_date_idx on aggregated_metrics (metric_key, metric_date);
create index if not exists aggregated_metrics_correlation_idx on aggregated_metrics (correlation_id);
