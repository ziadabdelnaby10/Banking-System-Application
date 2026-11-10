-- Dev/test branches. The codes match the branch_code values seeded for Identity users (HQ, BR001).
-- Ids are fixed UUIDv7-shaped literals so the script is idempotent.
insert into branches (id, code, name, region, status)
values ('01900000-0000-7000-8000-000000000001', 'HQ', 'Head Office', 'Cairo', 'ACTIVE'),
       ('01900000-0000-7000-8000-000000000002', 'BR001', 'Downtown Branch', 'Cairo', 'ACTIVE'),
       ('01900000-0000-7000-8000-000000000003', 'BR002', 'Alexandria Branch', 'Alexandria', 'ACTIVE')
on conflict (code) do nothing;
