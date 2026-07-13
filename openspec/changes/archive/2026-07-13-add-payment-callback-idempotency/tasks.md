## 1. Database And Domain Model

- [x] 1.1 Add `payment_callback_record` DDL to `sql/schema.sql` with callback number, trade number, nullable payment/order references, optional third-party trade number, payment status, amount, callback time, process status, failure reason, raw payload, timestamps, a unique key on `callback_no`, and indexes for `trade_no`, `payment_record_id`, and `order_id`.
- [x] 1.2 Add a `PaymentCallbackRecord` entity matching the table fields.
- [x] 1.3 Add callback process status constants or enum for processing, processed, duplicate, failed, and ignored outcomes.
- [x] 1.4 Add payment status constants or enum for `PAYING`, `SUCCESS`, `FAILED`, and `CLOSED` so local integer literals are not spread through payment code.
- [x] 1.5 Add DTO/VO classes for mock callback request and payment initiation response, including `tradeNo`, `callbackNo`, `thirdTradeNo`, `payStatus`, `amount`, and `callbackTime` for the callback request.
- [x] 1.6 Ensure `payment_callback_record.payment_record_id` and `order_id` are nullable so unknown `tradeNo` callbacks can be recorded.
- [x] 1.7 Confirm or preserve the existing unique key on `payment_record.trade_no` so callback lookup by `tradeNo` is unambiguous.
- [x] 1.8 Treat `thirdTradeNo` as optional in the mock callback DTO and store it when provided.

## 2. Mapper And SQL

- [x] 2.1 Add `PaymentCallbackRecordMapper` with insert, select by callback number, and update/finalize methods needed by callback processing.
- [x] 2.2 Add `PaymentCallbackRecordMapper.xml` using MyBatis XML result mapping and SQL.
- [x] 2.3 Extend `PaymentRecordMapper` with select by trade number, select latest mock payment by order ID, update paying to success, update paying to failed, and close current paying payment methods.
- [x] 2.4 Update `PaymentRecordMapper.xml` with conditional status updates that require the expected previous status.
- [x] 2.5 Keep `OrdersMapper.updateToPaidById(...)` as the final `where status = PENDING_PAYMENT` guard for callback success.

## 3. Payment Initiation Flow

- [x] 3.1 Update `PUT /order/{id}/pay` so it validates order ownership and pending-payment status but does not update order status.
- [x] 3.2 Make payment initiation reuse an existing `MOCK` `PAYING` payment record for the same pending-payment order.
- [x] 3.3 Make payment initiation create a new `PAYING` payment record when no reusable paying record exists.
- [x] 3.4 Allow payment initiation to create a new `PAYING` payment record after the latest mock payment record for that pending order failed.
- [x] 3.5 Return payment initiation data containing order ID, order number, current order status, amount, trade number, `PAYING` payment status, and request time.
- [x] 3.6 Ensure payment initiation does not call `DishStockService.confirmLockedStock` and does not write `CONFIRM` stock records.
- [x] 3.7 Protect payment initiation check-and-create with the existing Redis order status lock or an equivalent database row lock.
- [x] 3.8 Ensure concurrent payment initiation cannot create multiple `PAYING` payment records for the same pending-payment order.

## 4. Mock Callback API

- [x] 4.1 Add `PaymentController` with `POST /payment/mock/callback`.
- [x] 4.2 Ensure the mock callback endpoint is available to the mock provider without normal user JWT in this stage.
- [x] 4.3 Validate required callback fields and supported `payStatus` values before applying side effects.
- [x] 4.4 Add a payment service boundary for callback processing instead of placing business logic in the controller.
- [x] 4.5 Store callback raw payload or a useful serialized representation when practical for audit/debugging.

## 5. Callback Idempotency And Validation

- [x] 5.1 Implement `callbackNo` idempotency: terminal callback records return success idempotently, while unfinished `PROCESSING` records remain retryable.
- [x] 5.2 Handle concurrent duplicate callback numbers with the database unique key and idempotent reload behavior.
- [x] 5.3 Match callbacks to `payment_record` by internal `tradeNo`.
- [x] 5.4 Record unknown `tradeNo` callbacks with failed process status and no order or stock side effects.
- [x] 5.5 Validate callback amount against `payment_record.amount` using `BigDecimal.compareTo`.
- [x] 5.6 Record amount mismatch callbacks with a failure reason and no payment success, order update, or stock confirmation.
- [x] 5.7 Treat `DUPLICATE` process status as a new callback number for an already finalized trade or order, not as repeated delivery of the same callback number.
- [x] 5.8 Ensure existing `callbackNo` returns idempotent success only for terminal callback process statuses, not unfinished `PROCESSING` records.
- [x] 5.9 Ensure technical failures during callback processing roll back callback handling or leave the callback retryable.
- [x] 5.10 Clarify and implement callback process status meanings: `DUPLICATE` for repeated successful business result with a new callback number, `IGNORED` for state-disallowed no-ops, and `FAILED` for validation failures.
- [x] 5.11 Log a warning when repeated `callbackNo` delivery carries inconsistent key fields such as different trade number, amount, or payment status.
- [x] 5.12 Define stale `PROCESSING` callback recovery: recent `PROCESSING` remains retryable/busy, while stale `PROCESSING` can be reclaimed or reset for retry.
- [x] 5.13 Document and implement that `payStatus=FAILED` and `process_status=FAILED` are different concepts; an accepted failed-payment callback can have `process_status=PROCESSED`.

## 6. Callback Result Handling

- [x] 6.1 Process `payStatus=FAILED` by recording the callback, conditionally updating a `PAYING` payment record to failed, leaving the order pending-payment, and leaving stock unchanged.
- [x] 6.2 Process the first valid `payStatus=SUCCESS` callback by acquiring the existing Redis order status lock for the order.
- [x] 6.3 Within the successful callback transaction, conditionally update the payment record from `PAYING` to successful with callback time and third-party trade number.
- [x] 6.4 Within the same transaction, conditionally update the order from `PENDING_PAYMENT` to `PAID` and set pay time.
- [x] 6.5 Confirm locked stock and write `CONFIRM` stock records only after the order status update affects one row.
- [x] 6.6 Mark the callback record processed only when the first successful callback side effects commit together.
- [x] 6.7 Treat new callback numbers for an already successful trade as idempotent business success without repeating order or stock updates.
- [x] 6.8 Treat successful callbacks after order cancellation or any non-pending order state as recorded business no-ops with no stock confirmation.
- [x] 6.9 Release the Redis order status lock after transaction completion when transaction synchronization is active.
- [x] 6.10 Close current `MOCK` `PAYING` payment records in the same transaction when manual cancellation successfully cancels a pending-payment order.
- [x] 6.11 Close current `MOCK` `PAYING` payment records in the same transaction when timeout cancellation successfully cancels a pending-payment order.
- [x] 6.12 Ensure late successful callbacks for closed payment records are recorded as ignored or duplicate business no-ops and do not mark the payment successful.
- [x] 6.13 Define terminal payment statuses so `SUCCESS`, `FAILED`, and `CLOSED` must not be changed by later callbacks for the same trade.
- [x] 6.14 Treat callbacks for terminal payment records as recorded business no-ops with no order or stock side effects.
- [x] 6.15 Ensure Redis order status lock acquisition failure during `SUCCESS` callback processing rolls back callback handling and remains retryable.
- [x] 6.16 Ensure cancellation close uses a conditional update that affects only current `MOCK` `PAYING` records for the cancelled order.
- [x] 6.17 Ensure `SUCCESS` callback stops before order update when `PAYING -> SUCCESS` affects zero rows, then records duplicate or ignored according to the current payment status.
- [x] 6.18 Ensure `FAILED` callbacks for terminal payment records are recorded as business no-ops and do not reverse payment status.
- [x] 6.19 Define callback response semantics: business-final results return provider success, while technical retryable failures do not.

## 7. Verification Tests

- [x] 7.1 Update existing `OrderServiceImplTest` payment tests so payment initiation returns `PAYING` data and does not update order status or confirm stock.
- [x] 7.2 Add tests proving payment initiation reuses an existing `PAYING` payment record.
- [x] 7.3 Add tests proving payment initiation can create a new `PAYING` record after a failed payment record.
- [x] 7.4 Add tests proving payment initiation lock or row guard prevents concurrent duplicate `PAYING` payment creation.
- [x] 7.5 Add tests proving duplicate callback number has no duplicate order update or stock confirmation.
- [x] 7.6 Add tests proving an existing `callbackNo` in `PROCESSING` status is not treated as terminal idempotent success.
- [x] 7.7 Add tests proving unknown trade number is recorded and has no order or stock side effects.
- [x] 7.8 Add tests proving amount mismatch is recorded and does not mark payment successful.
- [x] 7.9 Add tests proving failed callback marks payment failed, keeps order pending, and does not release or confirm stock.
- [x] 7.10 Add tests proving first successful callback updates payment record, updates order to paid, and confirms locked stock.
- [x] 7.11 Add tests proving successful callback confirms stock only after the order conditional update succeeds.
- [x] 7.12 Add tests proving repeated successful callbacks for the same trade do not write duplicate `CONFIRM` stock records.
- [x] 7.13 Add tests proving manual cancellation closes current `PAYING` mock payment records.
- [x] 7.14 Add tests proving timeout cancellation closes current `PAYING` mock payment records.
- [x] 7.15 Add tests proving successful callback after timeout cancellation records a no-op, does not reopen a closed payment record, and does not revive the order.
- [x] 7.16 Add tests proving Redis order status lock acquisition failure rolls back callback handling and keeps callback retryable.
- [x] 7.17 Add tests proving callbacks for `SUCCESS`, `FAILED`, and `CLOSED` terminal payment records do not reverse payment status or affect order/stock.
- [x] 7.18 Add tests proving a repeated `callbackNo` with inconsistent key fields logs a warning and does not reprocess the changed payload.
- [x] 7.19 Add tests proving a new callback number for an already successful trade is recorded as duplicate without duplicate stock confirmation.
- [x] 7.20 Add tests proving stale `PROCESSING` callback records can be reclaimed or reset, while recent `PROCESSING` records remain retryable/busy.
- [x] 7.21 Add tests proving `SUCCESS` callback does not update order or stock when `PAYING -> SUCCESS` affects zero rows.
- [x] 7.22 Add tests proving failed-payment callbacks can be processed with `process_status=PROCESSED`.
- [x] 7.23 Add controller tests for callback validation, optional `thirdTradeNo`, and delegation.
- [x] 7.24 Run `./mvnw test` and fix compile or test failures.

## 8. Documentation And OpenSpec Validation

- [x] 8.1 Update `docs/API_TEST.md` so the payment section uses `PUT /order/{id}/pay` followed by `POST /payment/mock/callback`.
- [x] 8.2 Update `docs/API_TEST.md` with SQL checks for `payment_record`, `payment_callback_record`, order status, and `CONFIRM` stock records.
- [x] 8.3 Update `docs/API_TEST.md` with duplicate callback number, stale/recent processing callback behavior, new callback number for finalized trade, terminal payment callbacks, amount mismatch, failed callback, pay-status-vs-process-status distinction, retry-after-failure, close-on-cancel, and late-callback-after-timeout verification steps.
- [x] 8.4 Update `docs/PROJECT_CONTEXT.md` with the new two-step mock payment flow, callback idempotency rules, and `payment_callback_record` table.
- [x] 8.5 Fix the `docs/PROJECT_CONTEXT.md` interface list to include `PUT /order/{id}/pay` if still missing.
- [x] 8.6 Run `openspec validate add-payment-callback-idempotency --strict`.
- [x] 8.7 Run `openspec validate --all --strict`.
