# ADR-005 Implementation Guide — Maker-Checker Approval Workflow

Audience: Junior Java / Spring Boot developer

Goal: Implement maker-checker approval engine so critical operations enter a pending state and are approved/rejected by a checker, per `ADR-005-maker-checker-approval-workflow.md`.

Prerequisites
- `approvals` table exists (see migration)
- Basic understanding of state transitions and transactional boundaries

Steps
1. Read the ADR: `Backend/docs/adr/ADR-005-maker-checker-approval-workflow.md`.
2. Create `ApprovalService` with methods:
   - `createApproval(entityType, entityId, action, makerId, payload)` → inserts a row in `approvals` with `PENDING` status
   - `decideApproval(approvalId, checkerId, decision, reason)` → validate and update status to `APPROVED` or `REJECTED`
3. Integrate with producers:
   - In services that perform critical actions (e.g., transfers, account freeze), modify the flow so that if an action meets `critical` criteria, instead of executing the action immediately, call `createApproval` and return `PENDING_APPROVAL` to the maker.
4. Implement approval processing:
   - When `decideApproval` approves, run the previously deferred operation in a transaction (e.g., call ledger posting). Ensure the operation's side effects (ledger rows, account updates) happen atomically.
   - If rejected, update the original entity status to `REJECTED` and write an audit entry.
5. Add APIs:
   - GET `/approvals?status=PENDING` for checker inbox
   - POST `/approvals/{id}/decide` to submit decision
6. Notifications and audit:
   - On create/decision, write an entry to `audit_logs` and publish an outbox event for UI update.
7. Tests:
   - Unit tests for `ApprovalService` behavior and state transitions.
   - Integration test: create a maker request, verify a pending approval row exists, then simulate checker approval and verify the deferred operation executed.

PR checklist
- `ApprovalService` methods implemented and covered by tests.
- Maker flow creates pending approvals for critical actions.
- Checker APIs return detailed context to safely approve/reject.
