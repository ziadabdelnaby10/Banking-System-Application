-- V5: permissions for the Account service (accounts, products, status changes).
-- Idempotent, mirroring V4. The Account service reads these codes from the JWT.

insert into permissions (code, description)
values ('ACCOUNT_VIEW', 'View accounts, balances, status history and products'),
       ('ACCOUNT_CREATE', 'Open accounts for customers'),
       ('ACCOUNT_STATUS_CHANGE', 'Freeze, unfreeze or close accounts')
on conflict (code) do nothing;

-- ADMIN: everything.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
         join permissions p on p.code in ('ACCOUNT_VIEW', 'ACCOUNT_CREATE', 'ACCOUNT_STATUS_CHANGE')
where r.name = 'ADMIN'
on conflict (role_id, permission_id) do nothing;

-- MANAGER (checker): opens accounts and decides freeze / unfreeze / close.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
         join permissions p on p.code in ('ACCOUNT_VIEW', 'ACCOUNT_CREATE', 'ACCOUNT_STATUS_CHANGE')
where r.name = 'MANAGER'
on conflict (role_id, permission_id) do nothing;

-- TELLER (maker): opens and views accounts, cannot change their status.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
         join permissions p on p.code in ('ACCOUNT_VIEW', 'ACCOUNT_CREATE')
where r.name = 'TELLER'
on conflict (role_id, permission_id) do nothing;

-- AUDITOR: read-only.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
         join permissions p on p.code = 'ACCOUNT_VIEW'
where r.name = 'AUDITOR'
on conflict (role_id, permission_id) do nothing;
