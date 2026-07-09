## 1. Dependencies And Configuration

- [x] 1.1 Add Spring AMQP dependency to `pom.xml`.
- [x] 1.2 Add RabbitMQ connection properties for local Docker (`127.0.0.1:5672`, user `demo3`) to `application.properties`.
- [x] 1.3 Add order timeout properties for default 15-minute timeout, outbox publish schedule, max retry count, publisher confirm timeout, publisher claim timeout, and listener retry backoff.
- [x] 1.4 Add RabbitMQ constants for timeout exchanges, delay queue, cancel queue, and routing keys.
- [x] 1.5 Add RabbitMQ configuration for TTL delay queue, dead-letter exchange routing, cancel queue binding, listener retry behavior, and publisher confirm support.
- [x] 1.6 Enable scheduling if the project does not already enable it.

## 2. Database And Domain Model

- [x] 2.1 Add `order_timeout_outbox` DDL to `sql/schema.sql` with order ID, unique order ID, unique message ID, payload, expire time, status, retry count, next retry time, publish claim time, sent time, last error, timestamps, and useful indexes.
- [x] 2.2 Add or document SQL for the `system_timeout` user with password `12345`, nickname `订单超时系统`, and role `SYSTEM`.
- [x] 2.3 Add `OrderTimeoutOutbox` entity matching the table fields.
- [x] 2.4 Add timeout outbox status enum/constants for `PENDING`, `PUBLISHING`, `SENT`, and `FAILED`.
- [x] 2.5 Add timeout message payload DTO containing `orderId`, `messageId`, and `expireTime`.
- [x] 2.6 Add or update role constants/enums to support `SYSTEM` without granting normal admin API permissions.
- [x] 2.7 Ensure `SYSTEM` users cannot authenticate through normal frontend login APIs, or use an audit-only unusable password for `system_timeout`.

## 3. Mapper And SQL

- [x] 3.1 Add `OrderTimeoutOutboxMapper` with insert, select due rows, mark sent, mark failed/retryable, and optionally select by order ID methods.
- [x] 3.2 Add `OrderTimeoutOutboxMapper.xml` using MyBatis XML result mapping and SQL.
- [x] 3.3 Add mapper support to resolve the `system_timeout` user ID by username, reusing existing user mapper patterns when possible.
- [x] 3.4 Ensure due-row query only selects `PENDING` or retryable `FAILED` rows whose `next_retry_time` is due and retry count is below the configured max.
- [x] 3.5 Add conditional claim/update SQL for publisher ownership, such as `PENDING/FAILED -> PUBLISHING`, to avoid concurrent duplicate publishing.
- [x] 3.6 Ensure publisher claim SQL also checks `next_retry_time <= now()` and `retry_count < maxRetryCount`.
- [x] 3.7 Add stale `PUBLISHING` recovery SQL so publisher crashes do not leave timeout outbox rows stuck forever.

## 4. Order Submit Integration

- [x] 4.1 Add a timeout outbox service method that creates a pending outbox row with unique `message_id`, payload, and `expire_time`.
- [x] 4.2 Integrate timeout outbox creation into the first successful order submit transaction after the pending order ID is available.
- [x] 4.3 Ensure stock locking, order detail creation, cart cleanup, timeout outbox creation, and idempotency success marking commit together.
- [x] 4.4 Ensure order submit rollback leaves no committed timeout outbox row.
- [x] 4.5 Ensure idempotent duplicate submit returning an existing order ID does not create another outbox row or publish another timeout message.

## 5. Outbox Publisher

- [x] 5.1 Add a scheduled outbox publisher that scans due outbox rows.
- [x] 5.2 Publish each timeout payload to the RabbitMQ timeout delay exchange using the configured routing key.
- [x] 5.3 Use RabbitMQ publisher confirm so rows are marked `SENT` only after broker acknowledgement, using `message_id` as the correlation identifier when possible.
- [x] 5.4 On publish failure, negative acknowledgement, or confirm timeout, increment retry count, save `last_error`, and schedule the next retry.
- [x] 5.5 Keep rows in `FAILED` state with `last_error` when max retry count is reached.
- [x] 5.6 Keep publishing outside the HTTP request path so RabbitMQ unavailability does not fail successful order submission.
- [x] 5.7 Claim due outbox rows before publishing so overlapping scheduler runs or multiple app instances do not publish the same row concurrently.
- [x] 5.8 Document and test that the outbox publisher provides at-least-once delivery and duplicate timeout messages are handled by the idempotent consumer.
- [x] 5.9 Use `message_id` in publisher and consumer logs for tracing.
- [x] 5.10 Add stale `PUBLISHING` recovery so publisher crashes do not leave timeout outbox rows stuck forever.

## 6. Timeout Cancellation Consumer

- [x] 6.1 Add a RabbitMQ listener for the timeout cancel queue.
- [x] 6.2 Parse timeout message payload and acknowledge malformed payloads as handled failures with logging, without requeueing them.
- [x] 6.3 Add an order service method for system timeout cancellation instead of putting business logic in the listener.
- [x] 6.4 Resolve the `system_timeout` user ID and use it as the stock release operator.
- [x] 6.5 Acquire the existing Redis order status lock before timeout cancellation status updates or stock release.
- [x] 6.6 Treat missing orders and non-pending orders as idempotent no-ops.
- [x] 6.7 For pending-payment orders, conditionally update status to cancelled and set cancel time.
- [x] 6.8 Release locked stock and write `RELEASE` stock records only after the conditional status update affects one row.
- [x] 6.9 Ensure lock release happens after transaction completion when transaction synchronization is active.
- [x] 6.10 Let unexpected database, Redis, RabbitMQ, or stock release exceptions fail the listener attempt for retry.
- [x] 6.11 Treat Redis order status lock acquisition failure as a retryable technical conflict rather than an idempotent no-op.
- [x] 6.12 Use `orderId` as the business idempotency key and `messageId` mainly for tracing/logging.
- [x] 6.13 Configure listener retry with bounded attempts and backoff, and avoid infinite immediate requeue loops.

## 7. Verification Tests

- [x] 7.1 Add unit tests for timeout outbox row creation payload, status, retry count, and expire time.
- [x] 7.2 Add order submit tests proving first submit creates outbox and idempotent retry does not duplicate outbox.
- [x] 7.3 Add tests proving stock lock failure prevents outbox success from being committed in the submit flow.
- [x] 7.4 Add publisher tests for confirmed publish marking rows sent.
- [x] 7.5 Add publisher tests for failure, negative confirm, or timeout incrementing retry state and recording `last_error`.
- [x] 7.6 Add publisher tests for max retries leaving rows failed.
- [x] 7.7 Add consumer/service tests proving pending orders are cancelled and locked stock is released with the `system_timeout` operator.
- [x] 7.8 Add consumer/service tests proving paid, cancelled, or missing orders are idempotent no-ops with no stock release.
- [x] 7.9 Add tests proving timeout cancellation does not release stock when the conditional status update affects zero rows.
- [x] 7.10 Add tests proving timeout cancellation technical failures remain retryable.
- [x] 7.11 Add tests for overlapping publisher attempts proving only one publisher can claim and publish a due outbox row.
- [x] 7.12 Add tests proving malformed messages are acknowledged/dropped and do not enter endless retry.
- [x] 7.13 Add tests proving Redis lock acquisition failure keeps the timeout attempt retryable.
- [x] 7.14 Add tests or documented verification for late outbox publishing, explaining that actual cancellation can be later than `expire_time` when RabbitMQ is unavailable.
- [x] 7.15 Add tests proving stale `PUBLISHING` rows are recovered and become retryable.
- [x] 7.16 Add tests proving `SYSTEM` users cannot login through the normal login API.
- [x] 7.17 Run `./mvnw test` and fix compile or test failures.

## 8. Documentation And Manual Verification

- [x] 8.1 Update `docs/API_TEST.md` with RabbitMQ Docker setup and management UI login.
- [x] 8.2 Update `docs/API_TEST.md` with `system_timeout` initialization SQL.
- [x] 8.3 Update `docs/API_TEST.md` with local short-timeout verification steps, including how to temporarily use 30 seconds and noting that changing queue TTL may require deleting/redeclaring RabbitMQ timeout queues.
- [x] 8.4 Update `docs/API_TEST.md` with SQL checks for `order_timeout_outbox`, order cancellation, stock release, and `RELEASE` stock record operator.
- [x] 8.5 Update `docs/API_TEST.md` with RabbitMQ queue checks for TTL/DLX flow.
- [x] 8.6 Update `docs/PROJECT_CONTEXT.md` with RabbitMQ timeout cancellation, outbox, publisher confirm, system audit user, and next-step context for payment callback idempotency.
- [x] 8.7 Run `openspec validate add-rabbitmq-order-timeout-cancel --strict`.
- [x] 8.8 Run `openspec validate --all --strict`.
