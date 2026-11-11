-- Product catalogue seed (reference data needed in every environment). Ids are fixed UUIDv7-shaped literals so the
-- script is idempotent.
insert into account_products (id, code, name, account_type, min_balance, active)
values ('01900000-0000-7000-8000-0000000000a1', 'SAVINGS_STD', 'Standard Savings', 'SAVINGS', 0, true),
       ('01900000-0000-7000-8000-0000000000a2', 'CURRENT_STD', 'Standard Current', 'CURRENT', 0, true)
on conflict (code) do nothing;
