## Why

Order payment, cancellation, and completion all change the same order status. The current database conditional updates protect the final persisted state, but they do not prevent multiple concurrent requests from entering the business flow and creating avoidable intermediate work such as payment record attempts.

This change adds a Redis distributed lock around order status transitions to learn the real engineering pattern of guarding the process with Redis while keeping MySQL status conditions as the final consistency guard.

## What Changes

- Add Redis distributed locking for order status transition operations.
- Use one order-scoped lock key for payment, cancellation, and completion: `lock:order:status:{orderId}`.
- Implement the lock with `StringRedisTemplate`, `SET NX PX`, a random lock value, a finite TTL, and Lua-based compare-and-delete unlock.
- Return a clear business error when the lock cannot be acquired, such as "订单处理中，请稍后重试".
- Keep existing database conditional updates such as `where id = ? and status = ?`; Redis locking does not replace database consistency guards.
- Apply the lock to `OrderServiceImpl.pay()`, `OrderServiceImpl.cancel()`, and `OrderServiceImpl.complete()`.
- Ensure lock rejection does not create a successful payment record.
- Out of scope: Redisson, automatic lock renewal/watchdog, real payment callbacks, refund handling, and replacing MySQL status conditions.

## Capabilities

### New Capabilities

- `order-status-lock`: Protects order status transition operations with a Redis distributed lock while preserving database status-condition guards.

### Modified Capabilities

- `payment-record`: Clarifies that payment requests rejected before entering the payment flow, including lock acquisition failure, must not leave a successful payment record.

## Impact

- Redis: adds order status lock keys with finite TTL.
- Service: wraps `pay`, `cancel`, and `complete` status transitions in a Redis lock section.
- Utility/component: adds a small distributed lock helper using `StringRedisTemplate`.
- Database: no schema changes; existing order status conditional updates remain.
- API behavior: no endpoint path or response shape changes; concurrent requests may receive a business error asking the caller to retry later.
- Documentation/tests: add manual verification notes for repeated/concurrent payment, cancellation, and completion attempts.
