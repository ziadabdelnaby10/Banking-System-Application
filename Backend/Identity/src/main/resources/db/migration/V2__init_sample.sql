-- Seed data for local/dev testing.
-- Uses idempotent inserts so re-running is safe.

-- 1) Roles
insert into roles (name, description)
values ('ADMIN', 'System administrator with full access'),
       ('MANAGER', 'Branch manager with elevated business permissions'),
       ('TELLER', 'Front-office teller operations'),
       ('AUDITOR', 'Read-only audit and compliance access')
on conflict (name) do nothing;

-- 2) Permissions
insert into permissions (code, description)
values ('USER_CREATE', 'Create new users'),
       ('USER_VIEW', 'View user profiles'),
       ('USER_UPDATE', 'Update user profiles'),
       ('USER_LOCK', 'Lock or unlock user accounts'),
       ('ROLE_ASSIGN', 'Assign roles to users'),
       ('AUTH_LOGIN', 'Perform login'),
       ('AUTH_MFA_VERIFY', 'Verify MFA challenges'),
       ('AUDIT_VIEW', 'View audit logs')
on conflict (code) do nothing;

-- 3) Role -> Permission mapping
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
         join permissions p on p.code in (
                                          'USER_CREATE',
                                          'USER_VIEW',
                                          'USER_UPDATE',
                                          'USER_LOCK',
                                          'ROLE_ASSIGN',
                                          'AUTH_LOGIN',
                                          'AUTH_MFA_VERIFY',
                                          'AUDIT_VIEW'
    )
where r.name = 'ADMIN'
on conflict (role_id, permission_id) do nothing;

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
         join permissions p on p.code in (
                                          'USER_VIEW',
                                          'USER_UPDATE',
                                          'AUTH_LOGIN',
                                          'AUTH_MFA_VERIFY'
    )
where r.name = 'MANAGER'
on conflict (role_id, permission_id) do nothing;

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
         join permissions p on p.code in (
                                          'USER_VIEW',
                                          'AUTH_LOGIN'
    )
where r.name = 'TELLER'
on conflict (role_id, permission_id) do nothing;

insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
         join permissions p on p.code in (
                                          'USER_VIEW',
                                          'AUDIT_VIEW'
    )
where r.name = 'AUDITOR'
on conflict (role_id, permission_id) do nothing;

-- 4) Users
-- password_hash values are placeholders for local testing.
insert into users (id, username, email, password_hash, status, mfa_enabled, failed_login_count, branch_code)
values ('11111111-1111-7111-8111-111111111111', 'admin', 'admin@bank.local',
        '$2a$10$QmR2K3R6bVhEcXNrY2l0ZXh0dGVzdHBhc3M=', 'ACTIVE', true, 0, 'HQ'),
       ('22222222-2222-7222-8222-222222222222', 'manager1', 'manager1@bank.local',
        '$2a$10$QmR2K3R6bVhEcXNrY2l0ZXh0dGVzdHBhc3M=', 'ACTIVE', true, 0, 'BR001'),
       ('33333333-3333-7333-8333-333333333333', 'teller1', 'teller1@bank.local',
        '$2a$10$QmR2K3R6bVhEcXNrY2l0ZXh0dGVzdHBhc3M=', 'ACTIVE', false, 0, 'BR001'),
       ('44444444-4444-7444-8444-444444444444', 'auditor1', 'auditor1@bank.local',
        '$2a$10$QmR2K3R6bVhEcXNrY2l0ZXh0dGVzdHBhc3M=', 'ACTIVE', true, 0, 'HQ')
on conflict (id) do nothing;

-- 5) User -> Role mapping
insert into user_roles (user_id, role_id)
select u.id, r.id
from users u
         join roles r on r.name = 'ADMIN'
where u.username = 'admin'
on conflict (user_id, role_id) do nothing;

insert into user_roles (user_id, role_id)
select u.id, r.id
from users u
         join roles r on r.name = 'MANAGER'
where u.username = 'manager1'
on conflict (user_id, role_id) do nothing;

insert into user_roles (user_id, role_id)
select u.id, r.id
from users u
         join roles r on r.name = 'TELLER'
where u.username = 'teller1'
on conflict (user_id, role_id) do nothing;

insert into user_roles (user_id, role_id)
select u.id, r.id
from users u
         join roles r on r.name = 'AUDITOR'
where u.username = 'auditor1'
on conflict (user_id, role_id) do nothing;

-- 6) Optional sample login attempts
insert into login_attempts (id, user_id, username, ip_address, user_agent, result, failure_reason, correlation_id)
values ('aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa', '33333333-3333-7333-8333-333333333333', 'teller1', '10.10.1.25',
        'Mozilla/5.0', 'FAILED', 'INVALID_PASSWORD', 'corr-login-001'),
       ('bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb', '33333333-3333-7333-8333-333333333333', 'teller1', '10.10.1.25',
        'Mozilla/5.0', 'SUCCESS', null, 'corr-login-002'),
       ('cccccccc-cccc-7ccc-8ccc-cccccccccccc', '11111111-1111-7111-8111-111111111111', 'admin', '10.10.0.10',
        'PostmanRuntime/7.43.0', 'MFA_REQUIRED', null, 'corr-login-003')
on conflict (id) do nothing;