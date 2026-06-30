## 1. Redis Lock Component

- [x] 1.1 Add constants or helper methods for `lock:order:status:{orderId}` keys and lock failure messages.
- [x] 1.2 Create a Redis distributed lock helper using `StringRedisTemplate`.
- [x] 1.3 Implement `tryLock` with `SET NX PX`, a random lock value, and a finite TTL.
- [x] 1.4 Implement safe unlock with Lua compare-and-delete.
- [x] 1.5 Keep the helper small and focused; do not introduce Redisson or lock renewal.

## 2. Order Status Flow Integration

- [x] 2.1 Wrap `OrderServiceImpl.pay()` with order status lock acquire/release.
- [x] 2.2 Ensure `pay()` acquires the lock before creating a `payment_record`.
- [x] 2.3 Wrap `OrderServiceImpl.cancel()` with order status lock acquire/release.
- [x] 2.4 Wrap `OrderServiceImpl.complete()` with order status lock acquire/release.
- [x] 2.5 Return a clear business error when lock acquisition fails.

## 3. Database Final Guards

- [x] 3.1 Keep `OrdersMapper.updateToPaidById(...)` status-condition update unchanged.
- [x] 3.2 Keep `OrdersMapper.updateToCompletedById(...)` status-condition update unchanged.
- [x] 3.3 Strengthen cancel persistence with a database condition that only cancels pending-payment orders.
- [x] 3.4 Ensure failed conditional updates still produce a business error and do not leave successful payment records.

## 4. Verification And Documentation

- [x] 4.1 Run `./mvnw test` and fix any compile/test failures.
- [x] 4.2 Update `docs/API_TEST.md` with Redis lock and repeated/concurrent status transition verification notes.
- [x] 4.3 Manually verify repeated/concurrent payment requests result in only one successful payment and no extra successful payment records.
- [x] 4.4 Manually verify repeated/concurrent cancel and complete requests do not produce invalid final order states, and paid orders cannot be directly cancelled.
- [x] 4.5 Verify Redis lock keys are released after successful or failed status transition attempts.
