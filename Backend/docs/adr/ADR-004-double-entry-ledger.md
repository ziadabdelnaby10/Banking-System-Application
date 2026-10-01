# ADR-004: Double-Entry Ledger Design

Status: Accepted

## Context

Every financial movement must be traceable, auditable, and consistent across accounts and reporting views.

## Decision

Model all posted financial operations with a transaction record plus matching debit and credit ledger entries.

## Consequences

- Balance mutations are tied to ledger postings rather than direct updates.
- Reconciliation is easier because each posted transaction has explicit accounting entries.
- Posting logic must remain atomic and transactionally safe.
