## Context

The project already uses Redis for login state and dish-list caching. Order payment also now writes a `payment_record` row before updating the order to paid.

Current order status operations:

```text
pay()
  -> validate owner and status
  -> create MOCK payment_record
  -> update orders where id = ? and status = 1
  -> mark payment_record success

complete()
  -> validate status
  -> update orders where id = ? and status = 2

cancel()
  -> validate owner and status
  -> update orders to cancelled
```

Database status conditions protect the final persisted state for payment and completion, but concurrent requests can still enter the business flow. This change adds a Redis distributed lock before the order status business logic while keeping database status conditions as the final guard.

Request flow after this change:

```text
OrderController.pay / cancel / complete
  -> OrderServiceImpl method
  -> build lock key: lock:order:status:{orderId}
  -> try Redis SET key value NX PX ttl
  -> if lock fails: throw "订单处理中，请稍后重试"
  -> run existing auth/status/business logic
  -> execute database status-condition update
  -> finally unlock with Lua compare-and-delete
  -> Result return
```

## Goals / Non-Goals

**Goals:**

- Protect `pay`, `cancel`, and `complete` with an order-scoped Redis distributed lock.
- Use one lock key per order status transition target: `lock:order:status:{orderId}`.
- Implement the lock with `StringRedisTemplate`, a random lock value, finite TTL, and Lua compare-and-delete unlock.
- Keep MySQL status-condition updates as the final consistency guard.
- Strengthen `cancel` so only pending-payment orders can be cancelled at the database level.
- Ensure lock acquisition failure happens before creating successful payment records or changing order status.
- Keep API paths and successful response shapes unchanged.

**Non-Goals:**

- Do not introduce Redisson.
- Do not implement automatic lock renewal/watchdog.
- Do not remove database conditional updates.
- Do not implement real payment callbacks, refunds, or payment-provider idempotency keys.
- Do not add new database tables.

## Decisions

### Decision: Use One Order-Scoped Lock Key

The lock key will be:

```text
lock:order:status:{orderId}
```

Payment, cancellation, and completion all compete for the same order status. A single order-scoped status lock prevents `pay` and `cancel`, or `complete` and `cancel`, from running concurrently for the same order.

Alternative considered: separate keys such as `lock:order:pay:{orderId}` and `lock:order:cancel:{orderId}`. That would reduce contention by operation type, but it would not protect the shared order status consistently because different operations could still run at the same time.

### Decision: Use Low-Level Redis Lock For Learning

The lock helper will use:

```text
SET key value NX PX ttl
```

with a random value per lock attempt. Unlocking will use Lua:

```text
if redis.call("get", KEYS[1]) == ARGV[1] then
  return redis.call("del", KEYS[1])
else
  return 0
end
```

This teaches the underlying Redis lock pattern directly. Redisson is intentionally left for a later learning step.

### Decision: Use A Finite TTL And No Renewal

The initial TTL will be 30 seconds because the current order status transitions are short transactional operations.

If later work introduces real payment callbacks, provider calls, or other long-running operations inside the lock scope, the TTL must be re-evaluated or replaced with an automatic renewal mechanism.

Alternative considered: implement lock renewal. Renewal is useful for long-running production work, but current order status operations are short and synchronous. Adding renewal now would hide the basic lock mechanics behind more complexity.

### Decision: Keep Database Status Conditions

Redis lock controls process concurrency; database status conditions control final persisted correctness. Both are required for the intended engineering pattern.

Examples:

```sql
update orders
set status = 2, pay_time = ?
where id = ?
  and status = 1
```

`pay` and `complete` already follow this pattern. `cancel` should be strengthened so it updates only when the order is still pending payment. Paid orders should not be directly cancelled; a later refund flow should handle paid-order cancellation/refund behavior.

Example cancel guard:

```sql
update orders
set status = 4, cancel_time = ?
where id = ?
  and status = 1
```

### Decision: Fail Fast When Lock Cannot Be Acquired

If the lock is not acquired, the service should throw a business exception such as:

```text
订单处理中，请稍后重试
```

The request should not block-wait for a long time in this learning version. This keeps behavior simple and visible during manual testing.

### Decision: Acquire Lock Before Payment Record Creation

For `pay`, the lock should be acquired before creating a `payment_record`. This avoids creating a payment attempt for a request that never enters the payment flow.

The existing transaction around payment record creation, order update, and payment record success update should remain. If the database status update fails, the transaction should still roll back so no successful payment record is left behind.

## Risks / Trade-offs

- [Risk] Lock TTL expires before the business method finishes. → Mitigation: keep database status conditions as the final guard and choose a TTL much longer than the expected mock operation time.
- [Risk] A process crashes while holding the lock. → Mitigation: the TTL releases the lock eventually.
- [Risk] Unlock deletes another request's lock. → Mitigation: use random lock value and Lua compare-and-delete.
- [Risk] Redis is unavailable. → Mitigation: fail the status transition with a business error rather than proceeding without the intended lock in this learning change.
- [Risk] Locking increases operational dependency on Redis. → Mitigation: this project already depends on Redis for login state and cache, and this change is explicitly for distributed-lock learning.

## Migration Plan

1. Add a Redis lock helper component.
2. Add lock key constants/naming helpers for order status locks.
3. Wrap `pay`, `cancel`, and `complete` in lock acquire/release logic.
4. Strengthen `cancel` with a database status-condition update that only allows pending-payment orders to be cancelled.
5. Run `./mvnw test`.
6. Manually verify repeated/concurrent calls against a paid/cancelled/completed order and inspect payment records.

Rollback strategy: remove lock wrapping and helper usage while keeping the database status-condition guards.

## Open Questions

- None for this change.
