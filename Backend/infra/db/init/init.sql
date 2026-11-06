-- Bootstrap one database per microservice.
-- This script is executed by the Postgres Docker entrypoint on first init only.
-- Tables/schemas are intentionally managed by each service's Flyway migrations.

SELECT 'CREATE DATABASE bank_account OWNER ' || quote_ident(current_user)
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'bank_account')
\gexec

SELECT 'CREATE DATABASE bank_approval OWNER ' || quote_ident(current_user)
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'bank_approval')
\gexec

SELECT 'CREATE DATABASE bank_audit OWNER ' || quote_ident(current_user)
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'bank_audit')
\gexec

SELECT 'CREATE DATABASE bank_customer OWNER ' || quote_ident(current_user)
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'bank_customer')
\gexec

SELECT 'CREATE DATABASE bank_identity OWNER ' || quote_ident(current_user)
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'bank_identity')
\gexec

SELECT 'CREATE DATABASE bank_ledger OWNER ' || quote_ident(current_user)
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'bank_ledger')
\gexec

SELECT 'CREATE DATABASE bank_notification OWNER ' || quote_ident(current_user)
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'bank_notification')
\gexec

SELECT 'CREATE DATABASE bank_reporting OWNER ' || quote_ident(current_user)
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'bank_reporting')
\gexec

SELECT 'CREATE DATABASE bank_transaction OWNER ' || quote_ident(current_user)
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'bank_transaction')
\gexec

