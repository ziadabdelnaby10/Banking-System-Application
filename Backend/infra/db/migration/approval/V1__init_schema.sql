create table if not exists approvals (
    id bigserial primary key,
    entity_type varchar(80) not null,
    entity_id bigint not null,
    action varchar(80) not null,
    maker_id uuid not null,
    checker_id uuid,
    correlation_id varchar(64) not null,
    status varchar(20) not null default 'PENDING',
    reason text,
    created_at timestamptz not null default now(),
    decided_at timestamptz,
    constraint approvals_status_chk check (status in ('PENDING', 'APPROVED', 'REJECTED'))
);

create table if not exists approval_policies (
    id bigserial primary key,
    policy_code varchar(80) not null unique,
    description varchar(255),
    threshold_amount numeric(19,2),
    requires_checker boolean not null default true,
    enabled boolean not null default true,
    correlation_id varchar(64),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists approvals_status_created_at_idx on approvals (status, created_at);
create index if not exists approvals_entity_idx on approvals (entity_type, entity_id);
create index if not exists approvals_correlation_idx on approvals (correlation_id);
create index if not exists approval_policies_correlation_idx on approval_policies (correlation_id);
