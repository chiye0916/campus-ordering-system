## Why

The current order submission flow can create duplicate orders when a user double-clicks submit or when a client retries after a timeout. Adding idempotency to `POST /order/submit` protects the core trading entry point before inventory locking, payment callback idempotency, and RabbitMQ timeout cancellation are introduced.

## What Changes

- Require clients to send an `Idempotency-Key` header when submitting an order.
- Persist one idempotency record per current user and idempotency key.
- Return the original `orderId` when the same user retries the same order submission with the same key and same request fingerprint.
- Reject missing idempotency keys with `400`.
- Reject reuse of the same key with different request content using `409`.
- Reject duplicate requests while the first request is still processing using `409`.
- Keep the existing `Result<Long>` response shape for successful submissions.
- Keep order creation, order detail creation, cart cleanup, and idempotency success marking in one transaction.
- Out of scope: stock locking, payment callback idempotency, RabbitMQ timeout cancellation, Redis idempotency cache, and user refund request flow.

## Capabilities

### New Capabilities
- `order-submit-idempotency`: Defines idempotency key requirements, duplicate submit behavior, request fingerprint conflict handling, and persistence expectations for `POST /order/submit`.

### Modified Capabilities
- None.

## Impact

- Affected API: `POST /order/submit` now requires `Idempotency-Key`.
- Affected database: add an `order_idempotency` table with a unique constraint on `(user_id, idempotency_key)`.
- Affected backend code: `OrderController`, `OrderService`, `OrderServiceImpl`, new idempotency entity/mapper/XML, `sql/schema.sql`, and tests.
- Affected docs/frontend: API test documentation and any frontend order submission call must generate and send a stable idempotency key for each submit action.
