## 1. Database And Model

- [x] 1.1 Add `order_idempotency` DDL to `sql/schema.sql` with `user_id`, `idempotency_key`, `request_hash`, `order_id`, `status`, timestamps, and a unique key on `(user_id, idempotency_key)`.
- [x] 1.2 Add an `OrderIdempotency` entity matching the table fields.
- [x] 1.3 Add idempotency status constants or enum for `PROCESSING`, `SUCCEEDED`, and `FAILED`.
- [x] 1.4 Add `OrderIdempotencyMapper` interface methods for insert, select by user/key, mark succeeded, and mark failed if needed.
- [x] 1.5 Add `OrderIdempotencyMapper.xml` with MyBatis SQL and result mapping.

## 2. Submit API Contract

- [x] 2.1 Update `OrderController.submit` to read `Idempotency-Key` from the request header.
- [x] 2.2 Reject missing or blank `Idempotency-Key` with `400`.
- [x] 2.3 Update `OrderService.submit` signature and implementation call sites to accept the idempotency key.
- [x] 2.4 Update frontend/static order submit call to generate and send a stable `Idempotency-Key` for each submit action if the current UI submits orders.

## 3. Idempotency Flow

- [x] 3.1 Refactor submit flow so refreshed cart items can be reused for request hash calculation and order creation.
- [x] 3.2 Implement request hash generation from current user ID, remark, and cart items sorted by dish ID with dish ID, quantity, and dish price.
- [x] 3.3 Insert a `PROCESSING` idempotency record before creating the order for a new user/key.
- [x] 3.4 Keep order creation, order details, cart cleanup, and idempotency success marking in one transaction.
- [x] 3.5 Mark the idempotency record `SUCCEEDED` with the created `order_id` after order creation succeeds.
- [x] 3.6 Handle duplicate key insertion by loading the existing idempotency record.
- [x] 3.7 Return the existing `order_id` when the existing record has the same request hash and `SUCCEEDED` status.
- [x] 3.8 Reject same-key/different-hash requests with `409`.
- [x] 3.9 Reject same-key/same-hash requests with `PROCESSING` status with `409`.
- [x] 3.10 Reject or safely handle `FAILED` idempotency records without creating duplicate orders.

## 4. Verification

- [x] 4.1 Add unit tests for request hash stability and same-key/different-content detection.
- [x] 4.2 Add service or mapper tests for duplicate same-user/same-key handling.
- [x] 4.3 Verify first submit creates exactly one order, order details, cart cleanup, and succeeded idempotency record.
- [x] 4.4 Verify repeat submit with same key and same content returns the original order ID.
- [x] 4.5 Verify repeat submit with same key and different content returns `409` and creates no new order.
- [x] 4.6 Verify missing or blank `Idempotency-Key` returns `400`.
- [x] 4.7 Run `./mvnw test` and fix compile or test failures.

## 5. Documentation

- [x] 5.1 Update `docs/API_TEST.md` with `Idempotency-Key` examples for order submit.
- [x] 5.2 Update `docs/API_TEST.md` with duplicate retry and same-key/different-content verification steps.
- [x] 5.3 Update `docs/PROJECT_CONTEXT.md` with the order submit idempotency table, rules, and next-step context.
