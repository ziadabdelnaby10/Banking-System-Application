insert into branches (id, code, name, region, status)
values
    ('00000000-0000-0000-0000-000000000001', 'BR001', 'Downtown Branch', 'Cairo', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000002', 'BR002', 'Nile Branch', 'Giza', 'ACTIVE')
on conflict (code) do nothing;

