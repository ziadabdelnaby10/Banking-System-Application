create extension if not exists citext;
create extension if not exists pgcrypto;

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

create table if not exists users (
    id uuid primary key,
    username citext not null unique,
    email citext not null unique,
    password_hash varchar(255) not null,
    status varchar(20) not null default 'ACTIVE',
    mfa_enabled boolean not null default false,
    failed_login_count integer not null default 0,
    locked_until timestamptz,
    last_failed_login_at timestamptz,
    last_login_at timestamptz,
    branch_code varchar(20),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint users_status_chk check (status in ('ACTIVE', 'LOCKED', 'DISABLED'))
);

create table if not exists user_roles (
    user_id uuid not null,
    role_id bigint not null,
    assigned_at timestamptz not null default now(),
    primary key (user_id, role_id)
);

create table if not exists role_permissions (
    role_id bigint not null,
    permission_id bigint not null,
    granted_at timestamptz not null default now(),
    primary key (role_id, permission_id)
);

create table if not exists login_attempts (
    id uuid primary key,
    user_id uuid,
    username citext,
    ip_address inet,
    user_agent varchar(255),
    result varchar(20) not null,
    failure_reason varchar(80),
    correlation_id varchar(64),
    created_at timestamptz not null default now(),
    constraint login_attempts_result_chk check (result in ('SUCCESS', 'FAILED', 'LOCKED', 'MFA_REQUIRED'))
);

create table if not exists sessions (
    id bigserial primary key,
    user_id uuid not null,
    correlation_id varchar(64) not null,
    refresh_token_hash varchar(255) not null,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null default now()
);

create table if not exists mfa_challenges (
    id bigserial primary key,
    user_id uuid not null,
    correlation_id varchar(64) not null,
    method varchar(20) not null,
    code_hash varchar(255) not null,
    expires_at timestamptz not null,
    verified_at timestamptz,
    created_at timestamptz not null default now(),
    constraint mfa_challenges_method_chk check (method in ('TOTP', 'SMS', 'EMAIL', 'PUSH'))
);

create index if not exists users_branch_status_idx on users (branch_code, status);
create index if not exists users_lockout_idx on users (status, locked_until, failed_login_count);
create index if not exists login_attempts_user_created_idx on login_attempts (user_id, created_at);
create index if not exists login_attempts_username_created_idx on login_attempts (username, created_at);
create index if not exists login_attempts_correlation_idx on login_attempts (correlation_id);
create index if not exists sessions_user_expires_idx on sessions (user_id, expires_at);
create index if not exists sessions_correlation_idx on sessions (correlation_id);
create index if not exists mfa_challenges_user_idx on mfa_challenges (user_id, created_at);
create index if not exists mfa_challenges_correlation_idx on mfa_challenges (correlation_id);

create or replace function set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create trigger trg_roles_updated_at before update on roles
for each row execute function set_updated_at();

create trigger trg_permissions_updated_at before update on permissions
for each row execute function set_updated_at();

create trigger trg_users_updated_at before update on users
for each row execute function set_updated_at();
