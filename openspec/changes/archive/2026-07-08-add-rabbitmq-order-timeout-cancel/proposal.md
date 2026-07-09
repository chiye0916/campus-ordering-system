## Why

Pending-payment orders currently keep locked stock until a user manually pays or cancels. Adding RabbitMQ-backed timeout cancellation lets the system automatically release stock for unpaid orders before payment callback idempotency and broader reliability work are introduced.

## What Changes

- Add RabbitMQ as the asynchronous messaging dependency for order timeout cancellation.
- Use RabbitMQ TTL plus Dead Letter Exchange to delay timeout-cancel checks without requiring the delayed-message plugin.
- Add an `order_timeout_outbox` table so order creation and the intent to send a timeout message are committed in the same MySQL transaction.
- Add an outbox publisher job that scans pending timeout messages, sends them to RabbitMQ, uses publisher confirm, and marks messages as sent or retryable.
- Add a timeout-cancel consumer that idempotently checks the order state when the delayed message is delivered.
- Automatically cancel orders that remain `PENDING_PAYMENT` after the configured timeout.
- Reuse the existing Redis order status lock and database conditional status update protections for automatic timeout cancellation.
- Release locked stock and write a `RELEASE` stock record when timeout cancellation succeeds.
- Add a `SYSTEM` audit user, `system_timeout`, for automatic timeout cancellation stock records.
- Keep the default timeout at 15 minutes, with API documentation explaining how to temporarily use a shorter value for local verification.
- Out of scope: RabbitMQ delayed-message plugin, payment callback idempotency, management UI for outbox messages, alerting platform, and a separate order timeout compensation scanner.

## Capabilities

### New Capabilities

- `order-timeout-cancel`: Defines automatic timeout cancellation behavior for pending-payment orders using RabbitMQ TTL plus DLX.
- `order-timeout-outbox`: Defines reliable persistence, publishing, retry, and confirmation rules for order timeout messages.

### Modified Capabilities

- `order-status`: Pending-payment orders can be cancelled by the system timeout flow in addition to user cancellation.
- `order-status-lock`: Automatic timeout cancellation must use the existing Redis order status lock and database status-condition final guard.
- `stock-record`: Timeout cancellation writes a `RELEASE` stock record using the `system_timeout` audit user as operator.
- `dish-stock`: Timeout cancellation releases locked stock with the same stock consistency rules as manual pending-payment cancellation.
- `order-submit-idempotency`: First successful order submission must create the timeout outbox record transactionally; idempotent retries must not create duplicate timeout messages.

## Impact

- Affected dependencies: add Spring AMQP / RabbitMQ support.
- Affected configuration: RabbitMQ connection settings, timeout duration, outbox publish schedule/retry settings, publisher confirm settings.
- Affected database: add `order_timeout_outbox`; initialize or document the `system_timeout` `SYSTEM` audit user.
- Affected backend code: RabbitMQ config, outbox entity/mapper/XML/service, scheduler, timeout publisher, timeout consumer, order submit transaction, timeout cancellation service method, stock release integration, tests.
- Affected docs: update `docs/API_TEST.md` and `docs/PROJECT_CONTEXT.md` with RabbitMQ Docker setup, local timeout verification, outbox checks, and timeout cancellation SQL checks.
