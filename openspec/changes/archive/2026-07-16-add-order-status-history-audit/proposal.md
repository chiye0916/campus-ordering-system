## Why

The order lifecycle now has many successful state-change sources: submit, payment callback, user cancellation, timeout cancellation, fulfillment, refund requests, and internal mock refund fallback operations. Adding a database-backed order status history makes those lifecycle changes traceable and easier to explain, test, and demonstrate.

## What Changes

- Add an `order_status_history` table that records every successful order status transition.
- Record order creation as the first history entry with `old_status = null` and `new_status = PENDING_PAYMENT`.
- Record successful payment, user cancellation, timeout cancellation, accept, delivery start, delivery completion, refund request approval/completion, and internal mock refund start/completion.
- Store old/new status codes, operation, operator id, operator role, Chinese reason text, trace id, and creation time.
- Add `OrderStatusChangeOperation` with fixed operation names and default Chinese reason text.
- Add `OrderStatusHistoryService` to insert history inside the same transaction as each order status change.
- Add `GET /order/{id}/status-history` to return a chronological status timeline.
- Reuse order detail visibility rules for status history access.
- Make audit insertion part of the transactional contract: if history insertion fails, the related order status change rolls back.
- Do not record failed attempts, no-op timeout messages, duplicate callbacks, non-status actions, field-level diffs, or business-object-specific foreign keys in this version.

## Capabilities

### New Capabilities

- `order-status-history-audit`: Persistence, query, and visibility rules for order status history records.

### Modified Capabilities

- `order-status`: Successful order status transitions must write corresponding order status history records in the same transaction, while failed/no-op transitions must not write history.

## Impact

- Database: add `order_status_history` table and indexes on `(order_id, create_time)` and `trace_id`.
- Backend: add entity, VO, enum, mapper/XML, service, and controller endpoint for order status history.
- Order flow: update existing order, payment, timeout, and refund request status-change paths to record history only after successful conditional status updates.
- Permissions: status history endpoint reuses existing order detail visibility boundaries.
- Tests: add unit tests for history creation on each successful transition, no history on no-op/failed transitions, rollback on history failure, trace id capture, and status-history query permissions.
- Documentation: later update API docs and project context to describe the order status timeline endpoint and audit semantics.
