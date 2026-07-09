## Context

The project now has order submit idempotency, Redis-protected order status transitions, and MySQL-backed stock locking with stock records. A pending-payment order locks stock during submit, confirms locked stock on payment, and releases locked stock on manual pending-order cancellation.

The remaining gap is automatic timeout cancellation. If a user submits an order and never pays or cancels it, the order can stay pending and keep stock locked indefinitely. This change adds a RabbitMQ-based timeout flow with an outbox table so the intent to send a timeout message is committed together with the order transaction.

The agreed local RabbitMQ setup is:

```text
host: 127.0.0.1
port: 5672
username: demo3
password: 12345
management: http://127.0.0.1:15672
```

The default order timeout is 15 minutes. API regression documentation can use a shorter local value such as 30 seconds for verification.

## Goals / Non-Goals

**Goals:**

- Add RabbitMQ with TTL plus Dead Letter Exchange for delayed order timeout checks.
- Add an `order_timeout_outbox` table to persist timeout-message send intent inside the order submit transaction.
- Add a scheduled outbox publisher that sends pending timeout messages and uses RabbitMQ publisher confirm before marking them sent.
- Add a timeout consumer that idempotently cancels only orders that are still pending payment.
- Reuse Redis order status locks and database conditional status updates for timeout cancellation.
- Release locked stock and write `RELEASE` stock records when timeout cancellation succeeds.
- Use a `SYSTEM` audit user named `system_timeout` as the stock-record operator for automatic timeout cancellation.
- Keep this stage testable with Mockito-heavy unit tests and HTTP/SQL regression documentation.

**Non-Goals:**

- Do not use the RabbitMQ delayed-message plugin in this change.
- Do not add payment callback idempotency.
- Do not build a management UI for outbox messages.
- Do not add a monitoring or alerting platform.
- Do not add a separate order timeout compensation scanner in this change.
- Do not introduce microservices, Spring Security, MyBatis-Plus, Redis Lua, or Testcontainers in this change.

## Decisions

### Decision: Use TTL Plus DLX Instead Of The Delayed-Message Plugin

The first version will use RabbitMQ's built-in message TTL and Dead Letter Exchange behavior:

```text
order.timeout.delay.exchange
  -> order.timeout.delay.queue
       x-message-ttl = configured timeout
       x-dead-letter-exchange = order.timeout.dead.exchange
       x-dead-letter-routing-key = order.timeout.cancel
       message expires after TTL
       dead-letters to:
  -> order.timeout.dead.exchange
       routes with key order.timeout.cancel to:
  -> order.timeout.cancel.queue
       consumed by timeout cancellation listener
```

This avoids requiring a RabbitMQ plugin while still matching the business need: one delayed check after order creation. The delayed-message plugin remains a reasonable later replacement if the project wants plugin-based delay semantics.

In this first version, the queue TTL starts when the outbox publisher successfully sends the message to the delay queue, not when the order is created. If RabbitMQ or the publisher is unavailable, actual cancellation can happen later than `expire_time`. This is accepted for this stage because a separate order compensation scanner is out of scope.

### Decision: Use An Order Timeout Outbox

Order creation and RabbitMQ publishing are not one database transaction. To avoid "order committed but timeout message not sent", order submit will write an `order_timeout_outbox` row inside the existing submit transaction:

```text
OrderService.submit
  insert order_idempotency PROCESSING
  create PENDING_PAYMENT order
  lock stock and write LOCK records
  create order details
  clear cart
  insert order_timeout_outbox PENDING
  mark idempotency SUCCEEDED
```

If the transaction rolls back, no outbox row remains. If RabbitMQ is temporarily unavailable after the transaction commits, the outbox row stays pending and can be retried.

Recommended table shape:

```text
order_timeout_outbox
  id
  order_id
  message_id
  payload
  expire_time
  status          1 PENDING, 2 PUBLISHING, 3 SENT, 4 FAILED
  retry_count
  next_retry_time
  publish_claim_time
  sent_time
  last_error
  create_time
  update_time
```

`order_id` should be unique because this stage creates one timeout message per order. `message_id` should also be unique and should be used for tracing and publisher confirm correlation when possible. The payload should include only stable identifiers:

```json
{
  "orderId": 1,
  "messageId": "uuid",
  "expireTime": "2026-07-08T21:15:00"
}
```

Exchange and routing key can live in code/config constants instead of being stored per row.

### Decision: Publish Outbox Rows With A Scheduled Job And Publisher Confirm

An `@Scheduled` publisher will scan rows whose status is `PENDING` or retryable `FAILED` and whose `next_retry_time` is due. Before publishing, it must claim each row with a conditional update:

```text
PENDING/FAILED -> PUBLISHING
where id = ?
  and status in (PENDING, FAILED)
  and next_retry_time <= now
  and retry_count < maxRetryCount
```

Only the publisher whose claim update affects one row may send that timeout message. This prevents overlapping scheduler executions or future multiple application instances from publishing the same due row at the same time.

The claim must set `publish_claim_time`. If the application crashes after claiming a row but before marking it `SENT` or `FAILED`, a later publisher run must recover stale `PUBLISHING` rows whose `publish_claim_time` is older than a configured claim timeout. Recovery can mark the stale row `FAILED` and schedule it for retry, or otherwise make it claimable again.

The publisher sends each claimed message to the RabbitMQ timeout delay exchange and waits for publisher confirmation. Only confirmed messages are marked `SENT`.

Recommended rules:

```text
maxRetryCount = 5
initial retry: next_retry_time <= now
claim success: PENDING/FAILED -> PUBLISHING, set publish_claim_time
publish confirm ack: PUBLISHING -> SENT
publish failure/nack/confirm timeout: PUBLISHING -> FAILED, retry_count + 1, last_error saved, next_retry_time delayed
stale publishing claim: PUBLISHING older than claim timeout -> FAILED/retryable
retry_count >= maxRetryCount: keep FAILED for manual inspection
```

This is more reliable than marking a message as sent after `convertAndSend` returns without knowing whether the broker accepted the publish. It is still intentionally simpler than a full alerting system or dedicated outbox management UI.

This design provides at-least-once message delivery, not exactly-once delivery. For example, RabbitMQ can confirm the publish and the later database update to mark the outbox row as `SENT` can fail. The row may then be retried and publish a duplicate timeout message. The consumer must remain idempotent and use the order status checks plus database conditional updates as the final guard.

### Decision: Use A SYSTEM Audit User For Automatic Cancellation

Create or document a system audit user:

```text
username: system_timeout
password: 12345
nickname: 订单超时系统
role: SYSTEM
```

This user is not a human operator and should not be exposed in the frontend login presets. Adding `SYSTEM` may require adding or updating role constants so it does not grant normal administrator API permissions. `SYSTEM` users should not authenticate through normal frontend login APIs; if a password is stored for local initialization, it is audit-only and should not grant app access. This change does not require a frontend entry for `SYSTEM` users. The timeout cancellation service should resolve its user ID by username and use that ID as `stock_record.operator_id` when releasing stock. The stock record remark should be:

```text
订单超时自动取消释放库存
```

This keeps stock records auditable without changing the `stock_record` schema to add `operator_type`.

### Decision: Timeout Cancellation Reuses Existing Status And Stock Protections

The timeout consumer should not directly update stock or call mapper methods from the listener. The listener should delegate to a service method such as:

```text
timeoutCancel(orderId)
```

The service flow should be:

```text
acquire Redis order status lock
if lock cannot be acquired: fail this listener attempt as retryable
load order
if order does not exist: ack as stale message
if status is not PENDING_PAYMENT: ack as idempotent no-op
conditional update PENDING_PAYMENT -> CANCELLED
if update affected 1 row:
  aggregate persisted order_detail rows by dishId
  release locked stock with operatorId = system_timeout.id
if update affected 0 rows:
  no stock release, ack as concurrent state change
release Redis lock after transaction completion
```

This mirrors manual cancellation but uses a system actor and idempotent no-op behavior for stale messages.

### Decision: Consumer Failure Handling Stays Practical For This Stage

The consumer should treat business-idempotent cases as successful:

```text
PAID, CANCELLED, ACCEPTED, DELIVERING, COMPLETED, REFUNDING, REFUNDED -> ack/no-op
missing order -> ack/no-op
```

Real technical failures should fail the listener path so Spring AMQP / RabbitMQ can retry according to configured listener retry behavior. Listener retry must be bounded with backoff, such as 3 to 5 attempts with increasing delay, and must avoid infinite immediate requeue loops. After retries are exhausted, this stage can log and reject without requeue; a separate failure queue or alerting platform is out of scope.

Malformed timeout payloads are not retryable because retrying the same malformed data cannot make it valid. The listener should log the malformed message and acknowledge or drop it without requeueing.

The consumer should use `orderId` as the business idempotency key. `messageId` is mainly for tracing, outbox uniqueness, and publisher confirm correlation; the consumer does not need to query the outbox table before deciding whether the order should be cancelled.

### Decision: Do Not Send Duplicate Timeout Messages For Idempotent Retries

Only the first successful submit that creates the order should create an outbox row. When `POST /order/submit` returns an existing order ID for an idempotent retry, it must not create another outbox row or send another timeout message.

## Risks / Trade-offs

- [Risk] Outbox adds more tables, scheduled work, and state transitions. -> Mitigation: keep the table narrow, statuses simple, and tests focused on submit, publish, retry, and consume behavior.
- [Risk] Publisher confirm can make the publishing path more complex. -> Mitigation: use it only in the outbox publisher, not in request handling, and mark messages sent only after confirmation.
- [Risk] RabbitMQ TTL starts at publish time, not order creation time. -> Mitigation: persist `expire_time`, document that cancellation can be later than `expire_time` if RabbitMQ/publisher is unavailable, and leave compensation scanning for a later reliability phase.
- [Risk] Outbox publishing can produce duplicate timeout messages after broker confirm but before the outbox row is marked sent. -> Mitigation: explicitly use at-least-once delivery semantics and keep the consumer idempotent.
- [Risk] A publisher can crash after claiming an outbox row as `PUBLISHING`. -> Mitigation: store `publish_claim_time` and recover stale `PUBLISHING` rows after a configured claim timeout.
- [Risk] Timeout cancellation can race with user payment. -> Mitigation: keep Redis order status locks and database `where status = PENDING_PAYMENT` guards as final protection.
- [Risk] Listener failures can cause immediate infinite requeue loops. -> Mitigation: configure bounded retry with backoff and reject without requeue after retries are exhausted.
- [Risk] Consumer retries can process the same timeout message more than once. -> Mitigation: make consumer logic idempotent by checking order status and releasing stock only after a successful conditional status update.
- [Risk] The `system_timeout` user might be treated as a normal user by login code. -> Mitigation: block `SYSTEM` users from normal login flows and do not expose it in the frontend.
- [Risk] RabbitMQ unavailable for a long time can leave outbox rows unsent. -> Mitigation: retry up to a configured maximum and keep failed rows with `last_error` for manual inspection.

## Migration Plan

1. Add Spring AMQP dependency and RabbitMQ/order-timeout properties.
2. Add `order_timeout_outbox` DDL and document `system_timeout` initialization SQL.
3. Add RabbitMQ exchange, queue, routing-key, TTL, listener retry, and publisher confirm configuration.
4. Add outbox entity, mapper/XML, status enum, service, and scheduled publisher.
5. Integrate outbox row creation into first successful order submission.
6. Add timeout consumer and timeout cancellation service method.
7. Add Mockito unit tests and run `./mvnw test`.
8. Update `docs/API_TEST.md` and `docs/PROJECT_CONTEXT.md`.

Rollback strategy: disable the scheduler and listener first, then remove submit-time outbox creation. Existing pending-payment orders can still be manually cancelled with the current API.

## Resolved Implementation Details

- The first version uses RabbitMQ TTL plus DLX instead of the delayed-message plugin.
- The timeout outbox provides at-least-once message delivery, not exactly-once delivery.
- Duplicate timeout messages are handled by idempotent order-status checks and database conditional updates.
- The first version accepts that actual timeout cancellation can be later than `expire_time` if RabbitMQ or the outbox publisher is unavailable.
- Automatic timeout cancellation uses a `SYSTEM` audit user named `system_timeout`.
