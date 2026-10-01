create extension if not exists citext;
create extension if not exists pgcrypto;

create table if not exists branches (
    id bigserial primary key,
    code varchar(20) not null unique,
    name varchar(150) not null,
    region varchar(100),
    status varchar(20) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint branches_status_chk check (status in ('ACTIVE', 'INACTIVE'))
);

create table if not exists users (
    id bigserial primary key,
    username citext not null unique,
    email citext not null unique,
    password_hash varchar(255) not null,
    status varchar(20) not null default 'ACTIVE',
    mfa_enabled boolean not null default false,
    last_login_at timestamptz,
    branch_id bigint references branches(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint users_status_chk check (status in ('ACTIVE', 'LOCKED', 'DISABLED'))
);

create table if not exists roles (
    id bigserial primary key,
    name varchar(80) not null unique,
    description varchar(255),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists permissions (
    id bigserial primary key,
    code varchar(120) not null unique,
    description varchar(255),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists user_roles (
    user_id bigint not null references users(id) on delete cascade,
    role_id bigint not null references roles(id) on delete cascade,
    assigned_at timestamptz not null default now(),
    primary key (user_id, role_id)
);

create table if not exists role_permissions (
    role_id bigint not null references roles(id) on delete cascade,
    permission_id bigint not null references permissions(id) on delete cascade,
    granted_at timestamptz not null default now(),
    primary key (role_id, permission_id)
);

create table if not exists customers (
    id bigserial primary key,
    customer_no varchar(30) not null unique,
    full_name varchar(200) not null,
    dob date,
    national_id varchar(80) not null unique,
    risk_level varchar(20) not null default 'LOW',
    kyc_status varchar(20) not null default 'PENDING',
    branch_id bigint not null references branches(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint customers_risk_chk check (risk_level in ('LOW', 'MEDIUM', 'HIGH')),
    constraint customers_kyc_chk check (kyc_status in ('PENDING', 'VERIFIED', 'REJECTED'))
);

create table if not exists accounts (
    id bigserial primary key,
    account_no varchar(40) not null unique,
    customer_id bigint not null references customers(id),
    product_type varchar(50) not null,
    currency char(3) not null,
    status varchar(20) not null default 'ACTIVE',
    available_balance numeric(19,2) not null default 0,
    ledger_balance numeric(19,2) not null default 0,
    branch_id bigint not null references branches(id),
    opened_at timestamptz not null default now(),
    closed_at timestamptz,
    lock_version integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint accounts_status_chk check (status in ('ACTIVE', 'FROZEN', 'CLOSED', 'PENDING')),
    constraint accounts_currency_chk check (currency ~ '^[A-Z]{3}$')
);

create table if not exists transactions (
    id bigserial primary key,
    txn_ref varchar(40) not null unique,
    txn_type varchar(40) not null,
    amount numeric(19,2) not null,
    currency char(3) not null,
    from_account_id bigint references accounts(id),
    to_account_id bigint references accounts(id),
    status varchar(30) not null default 'DRAFT',
    idempotency_key varchar(80) not null unique,
    created_by bigint not null references users(id),
    approved_by bigint references users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint transactions_status_chk check (status in ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'POSTED', 'REJECTED', 'FAILED')),
    constraint transactions_amount_chk check (amount > 0),
    constraint transactions_currency_chk check (currency ~ '^[A-Z]{3}$')
);

create table if not exists ledger_entries (
    id bigserial primary key,
    txn_id bigint not null references transactions(id) on delete cascade,
    account_id bigint not null references accounts(id),
    entry_type varchar(10) not null,
    amount numeric(19,2) not null,
    currency char(3) not null,
    posted_at timestamptz not null default now(),
    constraint ledger_entries_type_chk check (entry_type in ('DEBIT', 'CREDIT')),
    constraint ledger_entries_amount_chk check (amount > 0),
    constraint ledger_entries_currency_chk check (currency ~ '^[A-Z]{3}$'),
    constraint ledger_entries_txn_account_entry_uk unique (txn_id, account_id, entry_type)
);

create table if not exists approvals (
    id bigserial primary key,
    entity_type varchar(80) not null,
    entity_id bigint not null,
    action varchar(80) not null,
    maker_id bigint not null references users(id),
    checker_id bigint references users(id),
    status varchar(20) not null default 'PENDING',
    reason text,
    created_at timestamptz not null default now(),
    decided_at timestamptz,
    constraint approvals_status_chk check (status in ('PENDING', 'APPROVED', 'REJECTED'))
);

create index if not exists approvals_status_created_at_idx on approvals (status, created_at);
create index if not exists approvals_entity_idx on approvals (entity_type, entity_id);

create table if not exists audit_logs (
    id bigserial primary key,
    actor_id bigint references users(id),
    action varchar(120) not null,
    entity_type varchar(80) not null,
    entity_id bigint,
    payload_json jsonb not null default '{}'::jsonb,
    ip_address inet,
    correlation_id varchar(64) not null,
    created_at timestamptz not null default now()
);

create index if not exists audit_logs_entity_created_at_idx on audit_logs (entity_type, entity_id, created_at);
create index if not exists audit_logs_actor_created_at_idx on audit_logs (actor_id, created_at);

create table if not exists mfa_challenges (
    id bigserial primary key,
    user_id bigint not null references users(id) on delete cascade,
    method varchar(20) not null,
    code_hash varchar(255) not null,
    expires_at timestamptz not null,
    verified_at timestamptz,
    created_at timestamptz not null default now(),
    constraint mfa_challenges_method_chk check (method in ('TOTP', 'SMS', 'EMAIL', 'PUSH'))
);

create table if not exists sessions (
    id bigserial primary key,
    user_id bigint not null references users(id) on delete cascade,
    refresh_token_hash varchar(255) not null,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null default now()
);

create table if not exists outbox_events (
    id bigserial primary key,
    aggregate_type varchar(80) not null,
    aggregate_id varchar(80) not null,
    event_type varchar(120) not null,
    payload_json jsonb not null,
    headers_json jsonb not null default '{}'::jsonb,
    status varchar(20) not null default 'NEW',
    retry_count integer not null default 0,
    created_at timestamptz not null default now(),
    published_at timestamptz,
    constraint outbox_events_status_chk check (status in ('NEW', 'PUBLISHED', 'FAILED'))
);

create index if not exists outbox_events_status_created_at_idx on outbox_events (status, created_at);
create index if not exists users_branch_status_idx on users (branch_id, status);
create index if not exists accounts_customer_status_idx on accounts (customer_id, status);
create index if not exists accounts_branch_status_idx on accounts (branch_id, status);
create index if not exists transactions_created_at_status_idx on transactions (created_at, status);
create index if not exists ledger_entries_account_posted_at_idx on ledger_entries (account_id, posted_at);

create or replace function set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create trigger trg_branches_updated_at before update on branches
for each row execute function set_updated_at();

create trigger trg_users_updated_at before update on users
for each row execute function set_updated_at();

create trigger trg_roles_updated_at before update on roles
for each row execute function set_updated_at();

create trigger trg_permissions_updated_at before update on permissions
for each row execute function set_updated_at();

create trigger trg_customers_updated_at before update on customers
for each row execute function set_updated_at();

create trigger trg_accounts_updated_at before update on accounts
for each row execute function set_updated_at();

create trigger trg_transactions_updated_at before update on transactions
for each row execute function set_updated_at();
