-- V3: referential integrity, MFA attempt tracking and cleanup indexes.
-- V1 intentionally shipped without foreign keys; the audit tables use RESTRICT so history is never
-- silently removed when a user row is deleted.

alter table user_roles
    add constraint user_roles_user_fk foreign key (user_id) references users (id) on delete restrict,
    add constraint user_roles_role_fk foreign key (role_id) references roles (id) on delete restrict;

alter table role_permissions
    add constraint role_permissions_role_fk foreign key (role_id) references roles (id) on delete restrict,
    add constraint role_permissions_permission_fk foreign key (permission_id) references permissions (id) on delete restrict;

alter table sessions
    add constraint sessions_user_fk foreign key (user_id) references users (id) on delete restrict;

alter table mfa_challenges
    add constraint mfa_challenges_user_fk foreign key (user_id) references users (id) on delete restrict;

alter table login_attempts
    add constraint login_attempts_user_fk foreign key (user_id) references users (id) on delete restrict;

-- Failed MFA verification attempts per challenge (brute force protection for 6-digit codes).
alter table mfa_challenges
    add column if not exists attempts integer not null default 0;
alter table mfa_challenges
    add constraint mfa_challenges_attempts_chk check (attempts >= 0);

-- Support the scheduled purge of expired sessions / challenges.
create index if not exists sessions_expires_at_idx on sessions (expires_at);
create index if not exists mfa_challenges_expires_at_idx on mfa_challenges (expires_at);
