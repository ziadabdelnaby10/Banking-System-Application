create table if not exists journal_batches (
    id bigserial primary key,
    batch_ref varchar(40) not null unique,
    source_service varchar(60) not null,
    source_event varchar(120),
    status varchar(20) not null default 'PENDING',
    correlation_id varchar(64) not null,
    created_at timestamptz not null default now(),
    posted_at timestamptz
);

create table if not exists ledger_entries (
    id bigserial primary key,
    batch_id bigint not null,
    txn_id bigint not null,
    account_id bigint not null,
    entry_type varchar(10) not null,
    amount numeric(19,2) not null,
    currency char(3) not null,
    correlation_id varchar(64) not null,
    posted_at timestamptz not null default now(),
    constraint ledger_entries_type_chk check (entry_type in ('DEBIT', 'CREDIT')),
    constraint ledger_entries_amount_chk check (amount > 0),
    constraint ledger_entries_currency_chk check (currency ~ '^[A-Z]{3}$')
);

create table if not exists account_balances (
    id bigserial primary key,
    account_id bigint not null unique,
    available_balance numeric(19,2) not null default 0,
    ledger_balance numeric(19,2) not null default 0,
    lock_version integer not null default 0,
    correlation_id varchar(64) not null,
    last_updated_at timestamptz not null default now(),
    created_at timestamptz not null default now()
);

create index if not exists ledger_entries_account_posted_idx on ledger_entries (account_id, posted_at);
create index if not exists ledger_entries_batch_idx on ledger_entries (batch_id, txn_id);
create index if not exists ledger_entries_correlation_idx on ledger_entries (correlation_id);
create index if not exists journal_batches_correlation_idx on journal_batches (correlation_id);
create index if not exists account_balances_last_updated_idx on account_balances (last_updated_at);
create index if not exists account_balances_correlation_idx on account_balances (correlation_id);
