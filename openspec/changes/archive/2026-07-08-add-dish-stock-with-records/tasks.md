## 1. Database And Domain Model

- [x] 1.1 Add `dish_stock` DDL to `sql/schema.sql` with non-null unique `dish_id`, non-negative `available_stock`, non-negative `locked_stock`, `version`, timestamps, and sensible defaults.
- [x] 1.2 Add `stock_record` DDL to `sql/schema.sql` with dish ID, optional order ID, change type, quantity, before/after stock values, operator ID, remark, create time, and indexes for dish ID, order ID, and create time.
- [x] 1.3 Add `DishStock` and `StockRecord` entities.
- [x] 1.4 Add stock change type constants or enum for `SET`, `LOCK`, `CONFIRM`, and `RELEASE`.
- [x] 1.5 Add DTO/VO objects needed for stock query and stock set APIs, using `availableStock` instead of ambiguous `stock`.

## 2. Mapper And SQL

- [x] 2.1 Add `DishStockMapper` with select by dish ID, `selectByDishIdForUpdate`, insert/upsert if needed, admin set available stock, lock stock, confirm locked stock, and release locked stock methods.
- [x] 2.2 Add `DishStockMapper.xml` using MyBatis XML and conditional updates for lock/confirm/release operations.
- [x] 2.3 Add `StockRecordMapper` for inserting stock records and optionally querying by dish or order for verification.
- [x] 2.4 Add `StockRecordMapper.xml` with result mapping and insert SQL.

## 3. Stock Service And Admin APIs

- [x] 3.1 Add a stock service that centralizes `setStock`, `lockStock`, `confirmLockedStock`, and `releaseLockedStock`.
- [x] 3.2 Implement administrator stock query endpoint `GET /dish/{id}/stock`.
- [x] 3.3 Implement administrator stock set endpoint `PUT /dish/{id}/stock`.
- [x] 3.4 Ensure stock set requires administrator permission and writes a `SET` stock record.
- [x] 3.5 Ensure stock set updates available stock without silently clearing locked stock.
- [x] 3.6 Allow administrator stock set to create a missing `dish_stock` row after verifying the dish exists.
- [x] 3.7 Reject stock query when the dish exists but stock has not been initialized.
- [x] 3.8 Reject negative `availableStock` in the admin stock set API.
- [x] 3.9 Define `SET.change_quantity` as `available_after - available_before` and store the admin user ID as `operator_id`.
- [x] 3.10 Use `selectByDishIdForUpdate` before writing stock records so before/after values are reliable.

## 4. Order Submit Stock Locking

- [x] 4.1 Integrate stock locking into the first successful order submission after idempotency `PROCESSING` insertion and after creating the pending-payment order main record to obtain `order_id`.
- [x] 4.2 Lock stock for every refreshed cart item by decreasing available stock and increasing locked stock.
- [x] 4.3 Write `LOCK` stock records linked to the created order.
- [x] 4.4 Ensure insufficient stock or missing stock row rejects order submission and creates no order.
- [x] 4.5 Ensure idempotent duplicate retries returning an existing order ID do not lock stock or write `LOCK` records again.
- [x] 4.6 Aggregate cart items by dish ID before stock locking.
- [x] 4.7 Lock stock in ascending dish ID order to reduce deadlock risk.
- [x] 4.8 Ensure partial stock lock failure rolls back all previously locked stock and the pending order in the same transaction.

## 5. Payment And Cancellation Stock Effects

- [x] 5.1 Confirm locked stock only after the payment order-status conditional update affects one row, in the same transaction as payment status and payment record updates.
- [x] 5.2 Write `CONFIRM` stock records linked to the paid order.
- [x] 5.3 Release locked stock only after the cancellation order-status conditional update affects one row, in the same transaction as order cancellation status update.
- [x] 5.4 Write `RELEASE` stock records linked to the cancelled order.
- [x] 5.5 Ensure stock failures during payment or cancellation roll back the order status change.
- [x] 5.6 Ensure accept, delivery, complete, refund start, and refund complete do not change stock.
- [x] 5.7 Aggregate order details by dish ID and process stock confirmation/release in ascending dish ID order.
- [x] 5.8 Ensure duplicate pay or duplicate cancel requests do not write duplicate `CONFIRM` or `RELEASE` stock records.

## 6. Verification

- [x] 6.1 Add unit or service tests for successful admin stock set and `SET` record creation.
- [x] 6.2 Add tests for successful order submit stock lock and `LOCK` record creation.
- [x] 6.3 Add tests for insufficient stock preventing order creation.
- [x] 6.4 Add tests for idempotent duplicate retry not locking stock again.
- [x] 6.5 Add tests for payment confirming locked stock and writing `CONFIRM` records.
- [x] 6.6 Add tests for pending cancellation releasing locked stock and writing `RELEASE` records.
- [x] 6.7 Add tests that admin set stock keeps locked stock unchanged.
- [x] 6.8 Add tests that missing stock rows prevent order submission.
- [x] 6.9 Add tests for multi-dish order rollback when one dish has insufficient stock.
- [x] 6.10 Add tests that duplicate payment does not confirm locked stock twice.
- [x] 6.11 Add tests that duplicate cancellation does not release locked stock twice.
- [x] 6.12 Add tests that duplicate cart items are aggregated before stock operations.
- [x] 6.13 Add or document a concurrency verification where many requests compete for limited stock and successful orders do not exceed available stock.
- [x] 6.14 Run `./mvnw test` and fix compile or test failures.

## 7. Documentation

- [x] 7.1 Update `docs/API_TEST.md` with admin stock set/query steps before order submit.
- [x] 7.2 Update `docs/API_TEST.md` with stock lock, payment confirm, cancellation release, and stock record SQL checks.
- [x] 7.3 Update `docs/PROJECT_CONTEXT.md` with `dish_stock`, `stock_record`, stock lifecycle, and next-step context for RabbitMQ timeout cancellation.
