create table if not exists branches (
    id bigserial primary key,
    code varchar(20) not null unique,
    name varchar(150) not null,
    region varchar(100),
    status varchar(20) not null default 'ACTIVE',
    correlation_id varchar(64),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint branches_status_chk check (status in ('ACTIVE', 'INACTIVE'))
);

create table if not exists customers (
    id bigserial primary key,
    customer_no varchar(30) not null unique,
    full_name varchar(200) not null,
    dob date,
    national_id varchar(80) not null unique,
    risk_level varchar(20) not null default 'LOW',
    kyc_status varchar(20) not null default 'PENDING',
    branch_id bigint not null,
    correlation_id varchar(64) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint customers_risk_chk check (risk_level in ('LOW', 'MEDIUM', 'HIGH')),
    constraint customers_kyc_chk check (kyc_status in ('PENDING', 'VERIFIED', 'REJECTED'))
);

create table if not exists kyc_records (
    id bigserial primary key,
    customer_id bigint not null,
    document_type varchar(50) not null,
    document_ref varchar(120),
    status varchar(20) not null default 'PENDING',
    reviewed_by varchar(120),
    reviewed_at timestamptz,
    notes text,
    correlation_id varchar(64) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists customers_branch_status_idx on customers (branch_id, kyc_status);
create index if not exists customers_correlation_idx on customers (correlation_id);
create index if not exists kyc_records_customer_status_idx on kyc_records (customer_id, status);
create index if not exists kyc_records_correlation_idx on kyc_records (correlation_id);

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

create trigger trg_customers_updated_at before update on customers
for each row execute function set_updated_at();

create trigger trg_kyc_records_updated_at before update on kyc_records
for each row execute function set_updated_at();
