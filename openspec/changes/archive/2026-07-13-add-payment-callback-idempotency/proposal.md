## Why

The current mock payment endpoint marks orders paid immediately, so the system cannot model real provider callbacks, duplicate callback delivery, amount mismatch handling, or late payment after timeout cancellation. This change splits mock payment initiation from mock payment result handling before later Redis cache and regression-test reliability work.

## What Changes

- Add `POST /payment/mock/callback` as the mock third-party payment callback endpoint.
- Add a `PaymentController` for payment callback APIs instead of expanding `OrderController`.
- Add a `payment_callback_record` table to record every unique mock third-party callback identified by `callbackNo` separately from the main `payment_record` payment order.
- Change `PUT /order/{id}/pay` so it initiates or reuses a `MOCK` payment-in-progress record and returns payment initiation data, but does not mark the order paid or confirm stock.
- Reuse an existing `PAYING` payment record for the same pending-payment order when the user repeats payment initiation.
- Prevent concurrent payment initiation from creating multiple `PAYING` payment records for the same pending-payment order.
- Allow a pending-payment order to initiate a new mock payment after a previous payment record failed.
- Let `POST /payment/mock/callback` perform the successful payment transition from `PENDING_PAYMENT` to `PAID`.
- Use `callbackNo` as the callback request idempotency key.
- Validate callback amount against `payment_record.amount` using `BigDecimal.compareTo`.
- Record amount mismatch, invalid trade numbers, failed callbacks, duplicate callbacks, and late callbacks after timeout cancellation without unsafe order or stock side effects.
- Return idempotent success only for callback records that reached terminal process status; unfinished `PROCESSING` callbacks remain retryable.
- Define stale `PROCESSING` callback recovery so stuck callback records can be reclaimed or reset instead of being treated as provider success.
- Treat `SUCCESS`, `FAILED`, and `CLOSED` payment records as terminal states that later callbacks cannot reverse.
- Define callback response semantics so business-final outcomes return mock-provider success while technical failures remain retryable.
- Use the existing Redis order status lock and database status-condition update as the final protection for successful callback processing.
- Treat Redis order status lock acquisition failure during successful callback processing as a retryable technical conflict, not as a final callback result.
- Confirm locked stock and write `CONFIRM` stock records only after the order status conditional update from pending-payment to paid succeeds.
- Close existing `PAYING` mock payment records when manual cancellation or timeout cancellation successfully cancels an order.
- Keep this stage based on Mockito/unit tests and HTTP/SQL regression documentation.
- Out of scope: real WeChat/Alipay integration, real signature verification, refund provider callbacks, payment reconciliation jobs, Testcontainers, Redis Lua, and changing the RabbitMQ timeout mechanism.

## Capabilities

### New Capabilities

- `payment-callback-idempotency`: Defines mock payment callback request recording, callback idempotency, amount validation, callback result handling, and late/duplicate callback behavior.

### Modified Capabilities

- `payment-record`: Mock payment initiation now creates or reuses a payment-in-progress record; payment success/failure is finalized by callback handling.
- `order-status`: The paid transition for mock payment is triggered by the successful callback flow instead of directly by `PUT /order/{id}/pay`.
- `order-status-lock`: Mock payment initiation must use a concurrency guard, and callback success processing must use the existing Redis order status lock and database conditional update guard.
- `dish-stock`: Successful callback processing confirms locked stock only after the order is conditionally updated to paid.
- `stock-record`: Successful callback processing writes `CONFIRM` records once; duplicate or late callbacks must not write duplicate `CONFIRM` records.

## Impact

- Affected APIs: `PUT /order/{id}/pay` response semantics change, and `POST /payment/mock/callback` is added.
- Affected database: add `payment_callback_record`; possibly add indexes or mapper methods for payment lookup and status updates.
- Affected backend code: payment controller, DTO/VO, payment callback service, payment record mapper/XML, order payment initiation flow, order status transition integration, stock confirmation integration, tests.
- Affected docs: update `docs/API_TEST.md` and `docs/PROJECT_CONTEXT.md` with the new payment initiation and callback flow.
