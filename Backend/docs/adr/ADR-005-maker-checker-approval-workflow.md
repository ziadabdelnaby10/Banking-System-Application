# ADR-005: Maker-Checker Approval Workflow

Status: Accepted

## Context

High-risk banking actions need internal control, review, and clear separation between request creation and approval.

## Decision

Require a maker-checker approval workflow for selected critical operations such as high-value transfers, account freezes, and role changes.

## Consequences

- Critical actions move through a pending approval state before posting.
- Checkers must capture a decision reason.
- Approval data becomes part of the audit trail and compliance story.
