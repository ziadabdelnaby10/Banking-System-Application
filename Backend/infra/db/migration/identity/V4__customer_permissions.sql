-- V4: permissions for the Customer service (branches, customers, KYC).
-- Idempotent, mirroring the V2 seed approach. The Customer service reads these codes from the JWT.

insert into permissions (code, description)
values ('BRANCH_VIEW', 'View branches'),
       ('BRANCH_MANAGE', 'Create and update branches'),
       ('CUSTOMER_VIEW', 'View customers'),
       ('CUSTOMER_CREATE', 'Register customers'),
       ('CUSTOMER_UPDATE', 'Update customer profiles'),
       ('CUSTOMER_STATUS_CHANGE', 'Block, unblock or close customers'),
       ('KYC_SUBMIT', 'Submit KYC documents for a customer'),
       ('KYC_REVIEW', 'Approve or reject submitted KYC documents (checker)')
on conflict (code) do nothing;

-- ADMIN: everything.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
         join permissions p on p.code in ('BRANCH_VIEW', 'BRANCH_MANAGE', 'CUSTOMER_VIEW', 'CUSTOMER_CREATE',
                                          'CUSTOMER_UPDATE', 'CUSTOMER_STATUS_CHANGE', 'KYC_SUBMIT', 'KYC_REVIEW')
where r.name = 'ADMIN'
on conflict (role_id, permission_id) do nothing;

-- MANAGER (checker): runs the branch, reviews KYC, changes customer status.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
         join permissions p on p.code in ('BRANCH_VIEW', 'CUSTOMER_VIEW', 'CUSTOMER_CREATE', 'CUSTOMER_UPDATE',
                                          'CUSTOMER_STATUS_CHANGE', 'KYC_REVIEW')
where r.name = 'MANAGER'
on conflict (role_id, permission_id) do nothing;

-- TELLER (maker): registers customers and submits KYC, cannot review.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
         join permissions p on p.code in ('BRANCH_VIEW', 'CUSTOMER_VIEW', 'CUSTOMER_CREATE', 'CUSTOMER_UPDATE',
                                          'KYC_SUBMIT')
where r.name = 'TELLER'
on conflict (role_id, permission_id) do nothing;

-- AUDITOR: read-only.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
         join permissions p on p.code in ('BRANCH_VIEW', 'CUSTOMER_VIEW')
where r.name = 'AUDITOR'
on conflict (role_id, permission_id) do nothing;
