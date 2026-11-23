-- V6: permissions for the Ledger service (journal posting, statements, balances, reconciliation).
-- Idempotent, mirroring V5. The Ledger service reads these codes from the JWT.

insert into permissions (code, description)
values ('LEDGER_VIEW', 'View journal batches, ledger entries and ledger balances'),
       ('LEDGER_POST', 'Post balanced journal batches to the ledger'),
       ('LEDGER_RECONCILE', 'Recompute an account balance from its ledger entries and compare it with the stored one')
on conflict (code) do nothing;

-- ADMIN: everything.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
         join permissions p on p.code in ('LEDGER_VIEW', 'LEDGER_POST', 'LEDGER_RECONCILE')
where r.name = 'ADMIN'
on conflict (role_id, permission_id) do nothing;

-- MANAGER (checker): posts and reconciles.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
         join permissions p on p.code in ('LEDGER_VIEW', 'LEDGER_POST', 'LEDGER_RECONCILE')
where r.name = 'MANAGER'
on conflict (role_id, permission_id) do nothing;

-- TELLER (maker): read-only; postings are produced by approved transactions, not typed in by tellers.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
         join permissions p on p.code = 'LEDGER_VIEW'
where r.name = 'TELLER'
on conflict (role_id, permission_id) do nothing;

-- AUDITOR: read-only plus reconciliation.
insert into role_permissions (role_id, permission_id)
select r.id, p.id
from roles r
         join permissions p on p.code in ('LEDGER_VIEW', 'LEDGER_RECONCILE')
where r.name = 'AUDITOR'
on conflict (role_id, permission_id) do nothing;
