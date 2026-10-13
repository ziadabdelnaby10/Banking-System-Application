create table if not exists transactions (
    id bigserial primary key,
    txn_ref varchar(40) not null unique,
    txn_type varchar(40) not null,
    amount numeric(19,2) not null,
    currency char(3) not null,
    from_account_id bigint,
    to_account_id bigint,
    status varchar(30) not null default 'DRAFT',
    idempotency_key varchar(80) not null unique,
    correlation_id varchar(64) not null,
    created_by uuid not null,
    approved_by uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint transactions_status_chk check (status in ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'POSTED', 'REJECTED', 'FAILED')),
    constraint transactions_amount_chk check (amount > 0),
    constraint transactions_currency_chk check (currency ~ '^[A-Z]{3}$')
);

create table if not exists payment_requests (
    id bigserial primary key,
    txn_id bigint not null unique,
    request_source varchar(50) not null,
    source_channel varchar(40),
    request_payload jsonb not null default '{}'::jsonb,
    correlation_id varchar(64) not null,
    created_at timestamptz not null default now()
);

create table if not exists idempotency_keys (
    id bigserial primary key,
    key_value varchar(128) not null unique,
    resource_type varchar(40) not null,
    resource_id varchar(80) not null,
    request_hash varchar(128) not null,
    response_code varchar(20) not null default '200',
    response_body jsonb,
    status varchar(20) not null default 'ACTIVE',
    correlation_id varchar(64) not null,
    created_by uuid,
    expires_at timestamptz not null default (now() + interval '7 days'),
    created_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now(),
    constraint idempotency_keys_status_chk check (status in ('ACTIVE', 'COMPLETED', 'REJECTED', 'EXPIRED')),
    constraint idempotency_keys_request_hash_chk check (length(trim(request_hash)) > 0),
    constraint idempotency_keys_key_value_chk check (length(trim(key_value)) > 0)
);

create index if not exists transactions_created_at_status_idx on transactions (created_at, status);
create index if not exists transactions_correlation_idx on transactions (correlation_id);
create index if not exists payment_requests_created_at_idx on payment_requests (created_at);
create index if not exists payment_requests_correlation_idx on payment_requests (correlation_id);
create index if not exists idempotency_keys_status_expires_at_idx on idempotency_keys (status, expires_at);
create index if not exists idempotency_keys_resource_type_resource_id_idx on idempotency_keys (resource_type, resource_id);
create index if not exists idempotency_keys_correlation_idx on idempotency_keys (correlation_id);

create or replace function set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create or replace function cleanup_expired_idempotency_keys()
returns integer
language plpgsql
as $$
declare
    deleted_count integer;
begin
    delete from idempotency_keys
    where status in ('ACTIVE', 'COMPLETED')
      and expires_at < now();

    get diagnostics deleted_count = row_count;
    return deleted_count;
end;
$$;

create trigger trg_transactions_updated_at before update on transactions
for each row execute function set_updated_at();
