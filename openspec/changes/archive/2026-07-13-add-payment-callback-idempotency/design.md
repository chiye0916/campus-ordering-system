## Context

The project currently has order submit idempotency, Redis-protected order status transitions, MySQL-backed stock locking, payment records, and RabbitMQ timeout cancellation. The current mock payment flow still collapses payment initiation and payment success into one API call:

```text
PUT /order/{id}/pay
  create payment_record
  update orders PENDING_PAYMENT -> PAID
  confirm locked stock
  mark payment_record SUCCESS
```

That flow is useful for a first demo but hides the failure modes a real payment provider introduces: duplicate callback delivery, callback retries with the same callback number, multiple callback requests for the same trade number, amount mismatch, failed payment callbacks, and late success callbacks after an unpaid order has already timed out and released stock.

This change keeps the payment provider as `MOCK`, but separates payment initiation from callback result handling:

```text
PUT /order/{id}/pay
  create or reuse a PAYING payment_record
  return tradeNo and payment initiation data

POST /payment/mock/callback
  record callback request
  validate payment record and amount
  update payment record result
  if first successful callback and order is still pending:
    update order to PAID
    confirm locked stock
```

## Goals / Non-Goals

**Goals:**

- Add `POST /payment/mock/callback` in a new `PaymentController`.
- Add `payment_callback_record` as the audit table for unique mock third-party callbacks identified by `callbackNo`.
- Keep `payment_record` as the main payment order/attempt record.
- Change `PUT /order/{id}/pay` so it starts or reuses a `PAYING` mock payment, without changing the order to `PAID`.
- Reuse the existing `PAYING` payment record for repeated payment initiation on the same pending-payment order.
- Prevent concurrent payment initiation from creating multiple `PAYING` payment records for the same pending-payment order.
- Allow a new payment initiation after a previous mock payment record has failed and the order is still pending-payment.
- Make `callbackNo` the idempotency key for callback request handling.
- Treat duplicate callback delivery as successful from the mock provider perspective, without repeating order updates or stock confirmation.
- Validate callback amount with `BigDecimal.compareTo`.
- Use the existing Redis order status lock and database conditional update for successful callback processing.
- Confirm stock only after the `PENDING_PAYMENT -> PAID` database condition succeeds.
- Close existing `PAYING` mock payment records when manual cancellation or timeout cancellation successfully cancels an order.
- Keep verification focused on Mockito/unit tests and HTTP/SQL manual regression steps.

**Non-Goals:**

- Do not integrate real WeChat, Alipay, or other payment providers.
- Do not implement real signature verification or callback cryptographic validation.
- Do not add refund provider callbacks or user refund request payment integration.
- Do not add reconciliation jobs, payment close jobs, or callback retry scheduling.
- Do not introduce Testcontainers in this stage.
- Do not introduce MyBatis-Plus, Spring Security, microservices, Redis Lua, Redisson, or RabbitMQ delayed-message plugin changes.

## Decisions

### Decision: Create A Separate PaymentController

`POST /payment/mock/callback` will live in a new `PaymentController` instead of `OrderController`.

The request flow should be:

```text
PaymentController.mockCallback
  -> PaymentService.handleMockCallback
     -> PaymentCallbackRecordMapper / PaymentRecordMapper / OrdersMapper / OrderDetailMapper
     -> DishStockService.confirmLockedStock
     -> MyBatis XML / MySQL
```

This keeps order-user actions and third-party payment-provider actions separated. `OrderController` remains the user order API surface, while `PaymentController` owns mock payment-provider callbacks.

### Decision: Keep Mock Callback Public In This Stage

The mock callback endpoint represents a third-party provider calling the system, so it should not require the existing user JWT. This is acceptable because the stage is still a local learning demo and real provider signature verification is explicitly out of scope.

The controller should still validate request body fields:

```json
{
  "tradeNo": "PAY...",
  "callbackNo": "CB...",
  "thirdTradeNo": "THIRD...",
  "payStatus": "SUCCESS",
  "amount": 44.00,
  "callbackTime": "2026-07-09T10:00:00"
}
```

Recommended validation:

- `tradeNo`, `callbackNo`, and `payStatus` are required and non-blank.
- `payStatus` supports `SUCCESS` and `FAILED` in this stage.
- `amount` is required and non-negative.
- `thirdTradeNo` is optional in this mock stage and is stored when provided.
- `callbackTime` is required or defaulted by service to current time; prefer requiring it to make callback examples explicit.

### Decision: payment_record Is The Main Payment Order; payment_callback_record Is The Callback Log

`payment_record` already has the right main-payment fields: order identity, amount, channel, trade number, third-party trade number, status, request time, success time, callback time, and failure reason.

Add a separate `payment_callback_record` table for every unique mock provider callback identified by `callbackNo`:

```text
payment_callback_record
  id
  payment_record_id       nullable, so unknown tradeNo callbacks can still be recorded
  order_id                nullable, so unknown tradeNo callbacks can still be recorded
  trade_no
  callback_no
  third_trade_no
  pay_status
  amount
  callback_time
  process_status
  failure_reason
  raw_payload
  create_time
  update_time
```

Suggested status model:

```text
pay_status: SUCCESS, FAILED
process_status:
  1 PROCESSING
  2 PROCESSED
  3 DUPLICATE
  4 FAILED
  5 IGNORED
```

`callback_no` should be unique because it is the callback request idempotency key. This means repeated delivery of the same `callbackNo` reloads the existing row and returns success without inserting another row. The table records each unique callback, not every HTTP delivery attempt. `trade_no` should be indexed because many unique callback numbers can refer to one payment trade.

`payment_record.trade_no` already has a unique key in the current schema and must remain unique, because callback lookup by `tradeNo` must be unambiguous.

Process status meanings should be kept consistent:

```text
PROCESSING:
  callback handling has started but has not reached a terminal result

PROCESSED:
  first accepted callback completed its intended business effect

DUPLICATE:
  new callbackNo, but tradeNo has already completed SUCCESS processing
  example: repeated success notification with a different callbackNo

IGNORED:
  callback is recorded but business state does not allow the requested effect
  example: order already CANCELLED, payment_record already CLOSED, reverse callback for a terminal payment

FAILED:
  callback is recorded but business validation failed
  example: amount mismatch or unknown tradeNo
```

`DUPLICATE` does not describe repeated delivery of the same `callbackNo`, because those repeats do not create a new row.

Payment result status and callback process status are different concepts. A callback with `payStatus=FAILED` can have `process_status=PROCESSED` when the failed payment result is accepted and applied to the payment record. `process_status=FAILED` means callback validation or processing failed, not that the payment itself failed.

### Decision: Payment Initiation Reuses Existing PAYING Records

`PUT /order/{id}/pay` should only be available to the order owner for a pending-payment order. It should not change order status or confirm stock.

Recommended flow:

```text
OrderService.pay(orderId)
  load current user's order
  require order status PENDING_PAYMENT
  acquire concurrency guard for payment initiation
  find latest MOCK payment_record for order
  if existing status = PAYING:
    return existing tradeNo
  if existing status = FAILED:
    create new PAYING payment_record
  if no existing record:
    create new PAYING payment_record
  if existing status = SUCCESS:
    reject because order should no longer be pending
  release concurrency guard after transaction completion when applicable
```

Payment initiation does not update order status or stock, but it must still prevent concurrent creation of multiple `PAYING` records for the same pending-payment order. The preferred implementation for this project is to reuse the existing Redis order status lock around the check-and-create payment record operation. A database row lock on the order row would also be acceptable, but adding a specialized MySQL partial-unique-index workaround is not recommended for this stage.

If payment initiation cannot acquire its concurrency guard, it should return the existing busy/retry-style business error and must not create a payment record.

Callback success remains the operation that changes order status and confirms stock, so it must keep the stronger status-transition behavior described below.

The response should become a payment initiation VO, for example:

```text
orderId
orderNumber
orderStatus
amount
tradeNo
payStatus = PAYING
requestTime
```

The current `OrderPayVO` can be updated if its name remains understandable, or a clearer `PaymentInitiationVO` can be introduced.

### Decision: CallbackNo Drives Callback Idempotency

When a callback request arrives, the service should first check whether `callbackNo` already exists.

Recommended duplicate behavior:

```text
same callbackNo already recorded:
  if process_status in (PROCESSED, DUPLICATE, FAILED, IGNORED):
    return Result.success
    do not update payment_record
    do not update orders
    do not confirm stock
  if process_status = PROCESSING:
    if update_time is recent:
      treat as in-progress retryable technical conflict
      do not report provider success
    if update_time is stale:
      reclaim or reset the record for retry
      do not report provider success until this retry reaches a terminal result
```

This mirrors typical provider callback expectations without swallowing unfinished work: if the platform already terminally handled the callback request, return success so the provider does not retry forever; if a previous attempt is still unfinished, keep the callback retryable.

For a new `callbackNo`, insert a callback record inside the same transaction as the callback business effects when practical. If callback processing hits a technical failure, the transaction should roll back so the callback can be retried. If a `PROCESSING` row is left behind by an unexpected crash, later delivery of the same `callbackNo` must not be treated as terminal success.

Stale `PROCESSING` recovery should follow the same spirit as the timeout outbox stale-claim recovery: a recent `PROCESSING` row means another attempt may still be active, while a stale row can be reclaimed or reset by a later delivery of the same `callbackNo`. The timeout can be a small configuration property or a local constant for this stage; the key rule is that stale recovery must re-run validation and must not skip directly to provider success.

If a repeated delivery uses the same `callbackNo` but carries inconsistent key fields, such as a different `tradeNo`, amount, or `payStatus`, the system should log a warning and return the idempotent terminal result when the original callback is already terminal. It should not reprocess the changed payload.

### Decision: tradeNo And Amount Validation Happen Before Order Side Effects

Callback processing should load the `payment_record` by `tradeNo`.

Cases:

```text
tradeNo missing:
  record callback FAILED with reason
  return business success to mock provider

amount compareTo payment_record.amount != 0:
  record callback FAILED with reason
  do not update payment_record to SUCCESS
  do not update order
  do not confirm stock
  return business success to mock provider

payStatus = FAILED:
  record callback PROCESSED or FAILED-result
  update payment_record PAYING -> FAILED if it is still PAYING
  leave order PENDING_PAYMENT
  leave locked stock unchanged
```

Using `BigDecimal.compareTo` avoids rejecting semantically equal amounts such as `44.0` and `44.00`.

Only `PAYING` payment records can be changed by callbacks:

```text
PAYING -> SUCCESS  by valid SUCCESS callback
PAYING -> FAILED   by valid FAILED callback
PAYING -> CLOSED   by successful manual or timeout cancellation

SUCCESS, FAILED, CLOSED are terminal for callback handling.
Later callbacks for a terminal payment record are recorded as DUPLICATE, IGNORED, or FAILED according to process-status rules, but must not change payment_record, order status, or stock.
```

For a `SUCCESS` callback, the conditional payment update is the first gate:

```text
update payment_record
set status = SUCCESS
where trade_no = ?
  and status = PAYING
```

If this update affects zero rows, callback processing must reload the payment record and stop before order update or stock confirmation:

```text
current payment status = SUCCESS:
  record callback as DUPLICATE
current payment status = FAILED or CLOSED:
  record callback as IGNORED
unknown or inconsistent state:
  record callback as FAILED or keep retryable according to the failure type
```

For a `FAILED` callback, the same terminal-state rule applies. If the payment record is already `SUCCESS`, `FAILED`, or `CLOSED`, the callback is recorded as a business no-op and must not reverse payment status, order status, or stock.

### Decision: Successful Callback Uses Existing Order Status Protection

Successful callback processing is the new place where the order transitions to paid:

```text
handle SUCCESS callback
  load payment_record by tradeNo
  validate amount
  acquire lock:order:status:{orderId}
  if lock cannot be acquired:
    roll back callback handling and keep callback retryable
  load order
  if order is PENDING_PAYMENT:
    update payment_record PAYING -> SUCCESS
    update orders PENDING_PAYMENT -> PAID
    if order update affected 1 row:
      confirm locked stock and write CONFIRM stock records
      mark callback processed
  else:
    record callback as ignored or failed business state
    do not confirm stock
  release lock after transaction completion
```

The order status conditional update remains the final guard. Stock confirmation must happen only after that update affects one row. Duplicate callbacks for the same `tradeNo` after payment success should not repeat order or stock changes.

Redis order status lock acquisition failure during successful callback processing is a retryable technical conflict, not a business no-op. It must not finalize the callback record as `FAILED`, `IGNORED`, or `PROCESSED`, and it must not return mock-provider success unless another committed terminal result already exists for the same `callbackNo`.

Callback responses should distinguish business-final outcomes from retryable technical failures:

```text
business-final outcomes:
  unknown tradeNo
  amount mismatch
  terminal payment callback
  late success after cancelled order
  duplicate successful business result
  -> record terminal callback status and return provider success

technical retryable outcomes:
  Redis lock acquisition failure
  database exception
  stock confirmation exception
  transaction commit failure
  recent unfinished PROCESSING callback conflict
  -> do not finalize callback as success, failed, duplicate, or ignored
  -> do not return provider success
```

This keeps the mock provider from retrying requests that the system has intentionally and terminally handled, while preserving retry for failures that could still succeed later.

### Decision: Late Success After Timeout Is A Business No-Op To The Provider

If RabbitMQ timeout cancellation has already moved the order to `CANCELLED`, a later successful callback should be recorded but must not revive the order:

```text
order status = CANCELLED
  record callback
  do not update orders to PAID
  do not confirm stock
  return success to mock provider
  record reason such as "订单状态不允许支付成功"
```

This avoids infinite provider retries while preserving the internal audit trail. Real refund/compensation for late payment is out of scope.

### Decision: Failed Callback Does Not Cancel Orders Or Release Stock

`payStatus=FAILED` means the payment attempt failed, not that the order should be cancelled. The order remains `PENDING_PAYMENT`, locked stock remains locked, and the existing RabbitMQ timeout cancellation remains responsible for later cancellation and stock release.

After a failed payment record, the user can call `PUT /order/{id}/pay` again to create a new `PAYING` mock payment record with a new `tradeNo`, as long as the order is still pending-payment.

### Decision: Cancellation Closes In-Progress Mock Payments

Manual cancellation and RabbitMQ timeout cancellation already change the order from `PENDING_PAYMENT` to `CANCELLED` and release locked stock only after the conditional order update succeeds. In this change, the same successful cancellation transaction should also close any current `MOCK` `PAYING` payment record for the order:

```text
manual cancel / timeout cancel:
  update orders PENDING_PAYMENT -> CANCELLED
  release locked stock
  conditionally update current MOCK PAYING payment_record -> CLOSED
```

This avoids leaving data like:

```text
order = CANCELLED
payment_record = PAYING
```

If a successful callback arrives later for the closed trade, the callback is recorded as ignored or duplicate business success, the order remains cancelled, and stock is not confirmed.

The close operation should only affect current in-progress mock payments:

```sql
update payment_record
set status = CLOSED
where order_id = ?
  and pay_channel = 'MOCK'
  and status = PAYING
```

It must run only after the order cancellation conditional update succeeds, so historical `FAILED`, `SUCCESS`, or already `CLOSED` payment records are not changed.

## Risks / Trade-offs

- [Risk] Public mock callback endpoint has no real authentication. -> Mitigation: keep it explicitly `MOCK`, document that real signature verification is out of scope, and avoid using this design as production payment security.
- [Risk] Splitting payment initiation and callback changes existing API expectations. -> Mitigation: document the new two-step flow in `docs/API_TEST.md`, update unit tests, and keep response fields clear.
- [Risk] Late successful callback after timeout cancellation leaves a successful external payment with an internally cancelled order. -> Mitigation: record the callback and return mock-provider success, but do not change order or stock; real compensation/refund is a later stage.
- [Risk] Multiple failed and paying records per order can complicate lookup. -> Mitigation: reuse existing `PAYING` records and only create a new payment record after failure; query the latest record by order and channel.
- [Risk] Concurrent payment initiation can create multiple `PAYING` records. -> Mitigation: protect the initiation check-and-create flow with the existing Redis order status lock or an equivalent database row lock.
- [Risk] Cancelled orders can otherwise retain `PAYING` payment records. -> Mitigation: close current in-progress mock payment records inside successful manual and timeout cancellation transactions.
- [Risk] Callback insert and duplicate handling can race. -> Mitigation: enforce a unique key on `callback_no` and treat duplicate-key insert as idempotent success after reloading.
- [Risk] An unfinished `PROCESSING` callback row can cause duplicate delivery to be incorrectly acknowledged as success. -> Mitigation: return idempotent success only for terminal callback process statuses and keep unfinished callback handling retryable.
- [Risk] A `PROCESSING` callback row can get stuck after a crash. -> Mitigation: define stale `PROCESSING` recovery so recent rows stay busy/retryable and stale rows can be reclaimed or reset.
- [Risk] Redis lock acquisition failure during callback success can be mistaken for business failure. -> Mitigation: treat it as a retryable technical conflict and roll back callback handling.
- [Risk] Later callbacks can try to reverse terminal payment states. -> Mitigation: allow callbacks to update only `PAYING` payment records; treat `SUCCESS`, `FAILED`, and `CLOSED` as terminal.
- [Risk] Updating payment record success before order status could leave inconsistent data if stock confirmation fails. -> Mitigation: keep payment record, order status, callback record, and stock confirmation in one transaction for the first successful callback.

## Migration Plan

1. Add `payment_callback_record` DDL and model/mapper/XML.
2. Add callback request DTO, callback response/VO if needed, callback status enums/constants, and payment service boundary.
3. Update payment initiation to return `PAYING` trade data without changing order status or stock, while preventing concurrent duplicate `PAYING` creation.
4. Close current `PAYING` mock payment records when manual or timeout cancellation succeeds.
5. Implement callback processing with callbackNo terminal-state idempotency, stale `PROCESSING` recovery, amount validation, payment record terminal-state rules, order status lock retry behavior, conditional paid update, and stock confirmation.
6. Add Mockito/unit tests for initiation reuse, concurrent initiation protection, failed payment retry, duplicate callback, unfinished callback retry behavior, amount mismatch, first success, repeated trade success, terminal payment callbacks, failed callback, close-on-cancel, and late success after cancellation.
7. Update `docs/API_TEST.md` and `docs/PROJECT_CONTEXT.md`.
8. Run `./mvnw test`, `openspec validate add-payment-callback-idempotency --strict`, and `openspec validate --all --strict`.

Rollback strategy: keep the database table harmless, temporarily disable or ignore `POST /payment/mock/callback`, and restore the old direct-success `PUT /order/{id}/pay` behavior if needed during local learning. No external provider state is involved in this mock stage.

## Open Questions

- None. Confirmed decisions: payment initiation reuses an existing `PAYING` record, payment initiation must be concurrency guarded, mock callback can be public in this stage, `thirdTradeNo` is optional, failed payment can be retried with a new trade number, amount comparison uses `BigDecimal.compareTo`, only `PAYING` payment records are callback-mutable, unfinished callbacks remain retryable with stale recovery, business-final callback results return provider success, technical failures do not, and successful cancellation closes current `PAYING` mock payment records.
