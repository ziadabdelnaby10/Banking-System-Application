create table if not exists accounts (
    id bigserial primary key,
    account_no varchar(40) not null unique,
    customer_id bigint not null,
    branch_id bigint not null,
    product_type varchar(50) not null,
    currency char(3) not null,
    status varchar(20) not null default 'ACTIVE',
    available_balance numeric(19,2) not null default 0,
    ledger_balance numeric(19,2) not null default 0,
    correlation_id varchar(64) not null,
    opened_at timestamptz not null default now(),
    closed_at timestamptz,
    lock_version integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint accounts_status_chk check (status in ('ACTIVE', 'FROZEN', 'CLOSED', 'PENDING')),
    constraint accounts_currency_chk check (currency ~ '^[A-Z]{3}$')
);

create table if not exists account_status_history (
    id bigserial primary key,
    account_id bigint not null,
    previous_status varchar(20),
    new_status varchar(20) not null,
    changed_by uuid,
    correlation_id varchar(64) not null,
    reason text,
    created_at timestamptz not null default now()
);

create index if not exists accounts_customer_status_idx on accounts (customer_id, status);
create index if not exists accounts_branch_status_idx on accounts (branch_id, status);
create index if not exists accounts_correlation_idx on accounts (correlation_id);
create index if not exists account_status_history_account_created_idx on account_status_history (account_id, created_at);
create index if not exists account_status_history_correlation_idx on account_status_history (correlation_id);

create or replace function set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create trigger trg_accounts_updated_at before update on accounts
for each row execute function set_updated_at();
