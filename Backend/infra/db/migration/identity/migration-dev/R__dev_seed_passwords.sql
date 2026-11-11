-- DEV ONLY (loaded via spring.flyway.locations in application-dev.yml, never in prod).
-- Replaces the invalid placeholder hashes seeded by V2 with a real bcrypt (cost 12) hash so the
-- seeded users can log in locally. Dev password for every seeded user: Dev@Passw0rd!2026
-- Repeatable migration: only touches rows that still carry the V2 placeholder, so it is idempotent
-- and never overwrites a password that was changed through the API.
update users
set password_hash = '$2a$12$xbmXJv2ujq9FIE6hB01tve6MtHQmwHAxU9AyhFefuhEnAGf2XsYPG'
where password_hash = '$2a$10$QmR2K3R6bVhEcXNrY2l0ZXh0dGVzdHBhc3M='
  and username in ('admin', 'manager1', 'teller1', 'auditor1');
