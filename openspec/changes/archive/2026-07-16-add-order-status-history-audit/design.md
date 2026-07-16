## Context

The order lifecycle now has several state-change sources:

```text
order submit
mock payment callback
user cancellation
timeout cancellation
merchant acceptance
delivery start
delivery completion
refund request approval/completion
internal mock refund fallback
```

Today `orders.status` stores only the current state. Logs and trace ids help diagnose issues, but there is no durable order-level timeline that shows who changed a status, when it changed, and why it changed. This change adds a database-backed audit table and a read endpoint for that timeline.

## Goals / Non-Goals

**Goals:**

- Persist every successful order status transition in `order_status_history`.
- Record order creation as `old_status = null`, `new_status = PENDING_PAYMENT`.
- Store status codes as integers, matching `orders.status`.
- Store operation, operator id, operator role, Chinese reason text, trace id, and creation time.
- Capture trace id from the existing trace context/MDC.
- Record system-triggered changes with `operator_role = SYSTEM`, using the existing `system_timeout` user id when available and `null` otherwise.
- Make history insertion part of the same transaction as the related status change.
- Add `GET /order/{id}/status-history`, ordered by `create_time asc, id asc`.
- Reuse the existing order detail visibility rules for the status history endpoint.

**Non-Goals:**

- No audit administration page.
- No failed-operation audit records.
- No field-level diffs.
- No history for non-order-status actions such as cart changes, catalog changes, or stock SET operations.
- No deletion, archive, or retention policy.
- No complex actor-type polymorphism.
- No `payment_record_id`, `refund_request_id`, `message_id`, or other business-object-specific foreign keys in this version.
- No backfill for existing historical orders in this version; history starts with new status changes after the feature is implemented.

## Decisions

### Decision: Store a focused `order_status_history` table

Create `order_status_history` with:

```text
id
order_id
order_number
user_id
old_status
new_status
operation
operator_id
operator_role
reason
trace_id
create_time
```

`old_status` is nullable for order creation. `new_status` is required. Both status fields store integer codes, matching `orders.status`.

Field constraints:

```text
old_status nullable
new_status not null
operator_id nullable
operator_role not null
reason not null
trace_id nullable
create_time not null
```

Indexes:

```text
idx_order_status_history_order_id_create_time_id(order_id, create_time, id)
idx_order_status_history_trace_id(trace_id)
```

Rationale: most reads are timeline reads for a single order, and trace id lookup helps debugging. Keeping the table focused avoids coupling status history to every business module.

Alternative considered: add foreign keys such as `payment_record_id`, `refund_request_id`, or `message_id`. This was rejected for v1 because operation, reason, and trace id provide enough context without creating a wide, sparse audit table.

### Decision: Use explicit operation enum with default Chinese reason text

Add `OrderStatusChangeOperation`:

```text
ORDER_SUBMIT              订单创建，进入待支付状态
PAYMENT_SUCCESS           支付成功确认订单状态
USER_CANCEL               用户取消待支付订单
TIMEOUT_CANCEL            订单超时自动取消
MERCHANT_ACCEPT           商家接单
DELIVERY_START            配送员开始配送
DELIVERY_COMPLETE         配送员完成订单
REFUND_REQUEST_APPROVE    退款申请审核通过
REFUND_REQUEST_COMPLETE   退款申请完成退款
INTERNAL_REFUND_START     管理员内部发起模拟退款
INTERNAL_REFUND_COMPLETE  管理员内部完成模拟退款
```

Service calls use the enum default reason unless a more specific reason is useful, such as appending a refund number. Reasons are stored as Chinese business descriptions because the project API docs and business messages are already Chinese.

Alternative considered: store only operation and derive text at read time. This was rejected because storing reason as a snapshot allows future wording changes without rewriting old history meaning.

### Decision: Record only successful status changes

Write history only after the related conditional order status update succeeds. If a conditional update affects zero rows, the flow must not write history.

Examples:

- duplicate successful payment callback: no order status update, no history
- timeout message for already-paid order: no order status update, no history
- refund request rejection: no order status update, no history

Rationale: this table is an order status timeline, not a failed-attempt log. Failed attempts remain in logs and business responses.

### Decision: Audit write participates in the business transaction

History insertion happens in the same transaction as the related status update. If history insertion fails, the whole status-changing business operation rolls back.

`OrderStatusHistoryService.recordChange(...)` must participate in the caller transaction and must not use `REQUIRES_NEW`. The preferred implementation is `Propagation.MANDATORY`, so accidental calls outside an existing transaction fail fast.

Rationale: the stage goal is audit consistency. After this change, if an order status changed successfully, the corresponding history record must exist.

### Decision: System operator uses existing system audit user when available

For system-triggered status changes, use:

```text
operator_role = SYSTEM
operator_id = id of system_timeout when resolvable, otherwise null
```

This applies to mock payment callback success and timeout cancellation.

Rationale: the project already has `system_timeout` as an internal audit subject. Reusing it keeps v1 simple without introducing additional system users such as `system_payment`.

### Decision: Query endpoint reuses order detail visibility

Add:

```text
GET /order/{id}/status-history
```

Return records ordered by:

```text
create_time asc, id asc
```

Visibility:

```text
USER: own visible orders only
MERCHANT: same scope as order detail
DELIVERY: same scope as order detail
ADMIN: all orders
SYSTEM: no normal HTTP access
```

Rationale: order status history is part of order detail, so it should not introduce a new authorization model.

For old visible orders that have no history records, the endpoint returns an empty list. This supports existing data created before the audit table existed.

### Decision: Trace id is best-effort for async/system flows

HTTP status-changing flows use the current MDC/trace context trace id. Async and system flows, such as RabbitMQ timeout cancellation, use the trace id already placed in MDC by the listener or scheduler when available. If no trace id is available, `order_status_history.trace_id` may be null.

Rationale: the project already has trace context support for HTTP and message-driven work. This change reuses that context without expanding the scope into a new trace propagation project.

### Request flow

Query status history:

```text
GET /order/{id}/status-history
  -> OrderController
  -> OrderService or OrderStatusHistoryService visibility guard
  -> OrderStatusHistoryMapper select by order id
  -> Result<List<OrderStatusHistoryVO>>
```

Write status history:

```text
status-changing service method
  -> load current order snapshot
  -> execute conditional status update
  -> require rows == 1 for success flows
  -> OrderStatusHistoryService.recordChange(...)
  -> OrderStatusHistoryMapper insert
  -> transaction commit
```

## Risks / Trade-offs

- [Risk] Missing a status-changing integration point would leave the timeline incomplete.  
  Mitigation: cover every existing status transition path in tasks and add focused tests for each operation enum.

- [Risk] Auditing inside the main transaction can roll back business operations if audit insertion fails.  
  Mitigation: this is intentional for audit consistency in this stage; keep the insert simple and local to MySQL.

- [Risk] `recordChange` could accidentally use a separate transaction and break audit consistency.  
  Mitigation: require it to participate in the caller transaction and forbid `REQUIRES_NEW`; prefer `Propagation.MANDATORY`.

- [Risk] System actions may not always resolve a system user id.  
  Mitigation: persist `operator_role = SYSTEM` and allow `operator_id = null` when `system_timeout` is unavailable.

- [Risk] Reason text may become inconsistent if callers write arbitrary strings.  
  Mitigation: store default Chinese reason text on `OrderStatusChangeOperation` and use it by default.

- [Risk] The history endpoint may expose order lifecycle data to the wrong actor.  
  Mitigation: reuse existing order detail visibility checks instead of introducing separate visibility rules.
