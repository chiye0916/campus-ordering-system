## 1. Data Model

- [x] 1.1 Add `order_status_history` table to `sql/schema.sql` with order snapshot fields, status codes, operation, operator fields, reason, trace id, and creation time.
- [x] 1.2 Make `old_status`, `operator_id`, and `trace_id` nullable; make `new_status`, `operator_role`, `reason`, and `create_time` required.
- [x] 1.3 Add indexes `idx_order_status_history_order_id_create_time_id(order_id, create_time, id)` and `idx_order_status_history_trace_id(trace_id)`.
- [x] 1.4 Add `OrderStatusHistory` entity.
- [x] 1.5 Add `OrderStatusHistoryVO` with old/new status codes and labels, operation, operation text, operator fields, reason, trace id, and create time.
- [x] 1.6 Add `OrderStatusChangeOperation` enum with operation names and default Chinese reason text.

## 2. Persistence And Service

- [x] 2.1 Add `OrderStatusHistoryMapper` interface.
- [x] 2.2 Add `OrderStatusHistoryMapper.xml` with insert and select-by-order-id ordered by `create_time asc, id asc`.
- [x] 2.3 Add `OrderStatusHistoryService` interface and implementation for `recordChange(...)` and order history query.
- [x] 2.4 Capture trace id from the existing trace context or MDC in the history service.
- [x] 2.5 Resolve system-triggered operator data as `operator_role = SYSTEM` and `operator_id = system_timeout id` when available, otherwise null.
- [x] 2.6 Ensure `recordChange(...)` participates in the caller transaction and does not use `REQUIRES_NEW`; prefer mandatory caller transaction semantics.
- [x] 2.7 Use the operation default Chinese reason when no explicit reason is provided.
- [x] 2.8 Ensure history insertion failure propagates and rolls back the caller transaction.

## 3. Status Change Integration

- [x] 3.1 Record `ORDER_SUBMIT` history when order submit creates a pending-payment order.
- [x] 3.2 Record `PAYMENT_SUCCESS` history only when successful payment callback changes the order from pending payment to paid.
- [x] 3.3 Record `USER_CANCEL` history only when user cancellation changes the order from pending payment to cancelled.
- [x] 3.4 Record `TIMEOUT_CANCEL` history only when timeout cancellation changes the order from pending payment to cancelled.
- [x] 3.5 Record `MERCHANT_ACCEPT` history only when accept changes the order from paid to accepted.
- [x] 3.6 Record `DELIVERY_START` history only when delivery start changes the order from accepted to delivering.
- [x] 3.7 Record `DELIVERY_COMPLETE` history only when completion changes the order from delivering to completed.
- [x] 3.8 Record `REFUND_REQUEST_APPROVE` history only when refund request approval changes the order to refunding.
- [x] 3.9 Record `REFUND_REQUEST_COMPLETE` history only when refund request completion changes the order to refunded.
- [x] 3.10 Record `INTERNAL_REFUND_START` history only when order-level internal refund start changes the order to refunding.
- [x] 3.11 Record `INTERNAL_REFUND_COMPLETE` history only when order-level internal refund completion changes the order to refunded.
- [x] 3.12 Ensure duplicate payment callbacks, timeout no-ops, refund request rejection, failed callbacks, and failed conditional updates do not write history.
- [x] 3.13 Do not backfill existing historical orders in this version; start recording only for new successful status changes after implementation.

## 4. Query API And Permissions

- [x] 4.1 Add `GET /order/{id}/status-history`.
- [x] 4.2 Reuse existing order detail visibility rules for status history access.
- [x] 4.3 Return status history ordered by `create_time asc, id asc`.
- [x] 4.4 Return an empty list for visible old orders that have no history records.
- [x] 4.5 Reject normal HTTP status history access for `SYSTEM`.

## 5. Tests

- [x] 5.1 Add unit tests for `OrderStatusChangeOperation` default Chinese reason text.
- [x] 5.2 Add service tests for `OrderStatusHistoryService` insert/query mapping, trace id capture, system operator fallback, and status label conversion.
- [x] 5.3 Add tests proving `recordChange(...)` requires/participates in caller transaction and does not commit independently.
- [x] 5.4 Add tests proving order submit writes `ORDER_SUBMIT` history.
- [x] 5.5 Add tests proving payment success writes one `PAYMENT_SUCCESS` history and duplicate callbacks do not write duplicates.
- [x] 5.6 Add tests proving user cancellation and timeout cancellation write history only when status actually changes.
- [x] 5.7 Add tests proving accept, delivery start, and delivery complete write their corresponding history operations.
- [x] 5.8 Add tests proving refund request approve/complete and internal refund start/complete write distinct operations.
- [x] 5.9 Add tests proving failed conditional updates and no-op flows do not write history.
- [x] 5.10 Add tests proving history insertion failure rolls back the related status change.
- [x] 5.11 Add controller tests for `GET /order/{id}/status-history` permission boundaries, response ordering, and empty list for visible orders with no history.
- [x] 5.12 Add an integration test for a representative lifecycle timeline if the existing Testcontainers suite remains practical.

## 6. Documentation And Verification

- [x] 6.1 Update `docs/API_TEST.md` with `GET /order/{id}/status-history` examples and expected timeline behavior.
- [x] 6.2 Update `docs/PROJECT_CONTEXT.md` after implementation to include `order_status_history`, operations, and audit rules.
- [x] 6.3 Run `./mvnw test`.
- [x] 6.4 Run `./mvnw verify -Pintegration-test` when Docker is available or document why it was skipped.
- [x] 6.5 Run `openspec validate add-order-status-history-audit --strict`.
