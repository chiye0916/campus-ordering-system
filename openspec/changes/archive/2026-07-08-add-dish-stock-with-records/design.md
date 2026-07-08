## Context

The project currently supports order submission idempotency and explicit order status transitions, but dish inventory is not represented separately from dish data. Users can submit orders for available dishes, yet the system does not prevent overselling or explain why stock changed.

This change introduces a MySQL-backed stock model:

```text
dish_stock    current stock state
stock_record  append-only audit records for stock changes
```

The agreed stock lifecycle is:

```text
admin set stock:
  available becomes the admin-set value

order submit:
  available_stock - quantity
  locked_stock + quantity

mock payment success:
  locked_stock - quantity

pending order cancellation:
  locked_stock - quantity
  available_stock + quantity
```

RabbitMQ timeout cancellation will reuse the cancellation release behavior in a later change.

## Goals / Non-Goals

**Goals:**

- Add `dish_stock` as the source of current stock state.
- Add `stock_record` as an audit trail for stock changes.
- Add administrator stock query and set APIs separate from dish creation.
- Lock stock during the first successful order submission.
- Confirm locked stock consumption when mock payment succeeds.
- Release locked stock when a pending-payment order is cancelled.
- Ensure duplicate idempotent submit retries do not lock stock again.
- Keep related stock update and stock record insert in the same transaction as order/payment/cancel operations.
- Use MySQL conditional updates first.

**Non-Goals:**

- Do not add Redis Lua stock pre-deduction.
- Do not add RabbitMQ timeout cancellation.
- Do not add multi-store, multi-warehouse, or supplier inventory.
- Do not add stock warning rules.
- Do not add stock adjustment approval workflow.
- Do not build a complex stock management UI beyond API/frontend wiring needed for this demo.

## Decisions

### Decision: Keep Dish And Stock Separate

Stock will not be stored on the `dish` table. Add:

```text
dish_stock
  id
  dish_id
  available_stock
  locked_stock
  version
  create_time
  update_time
```

`dish_id` should be unique and not null. `available_stock`, `locked_stock`, and `version` should be non-null with defaults of `0`. If the local MySQL version supports `CHECK` constraints, add non-negative checks for available and locked stock; otherwise enforce non-negative values in the service layer. This keeps product data and inventory state independent.

Alternative considered: add a `stock` column to `dish`. That is simpler but makes it harder to model locked stock, stock records, and future inventory workflows.

### Decision: Administrator Sets Stock Separately

First-version stock management APIs:

```text
GET /dish/{id}/stock
PUT /dish/{id}/stock
```

`PUT /dish/{id}/stock` sets the available stock to the administrator-provided value and records a `SET` stock record. The first version should keep `locked_stock` unchanged when setting available stock, so admin adjustments do not silently erase stock already locked by pending orders.

The request body should name the field `availableStock`, not `stock`, because this endpoint sets available stock rather than total stock:

```json
{
  "availableStock": 100,
  "remark": "initial stock"
}
```

If a dish exists but does not yet have a `dish_stock` row, administrator stock set should create the stock row. If the dish does not exist, the request should fail with a dish-not-found business error. `GET /dish/{id}/stock` should reject missing stock rows instead of returning zeroes so "not initialized" is not confused with "initialized to zero".

`PermissionChecker.requireAdmin()` remains the authorization mechanism. `ADMIN` is still the first-version maximum-permission demo operator.

### Decision: Use Explicit Stock Operation Methods

The stock service should expose operation-oriented methods:

```text
setStock(dishId, availableStock, operatorId, remark)
lockStock(orderId, items)
confirmLockedStock(orderId, items)
releaseLockedStock(orderId, items)
```

These methods hide SQL details from `OrderServiceImpl` and centralize stock record creation.

### Decision: Use Conditional Updates For Stock Correctness

Lock stock with a database condition:

```sql
update dish_stock
set available_stock = available_stock - ?,
    locked_stock = locked_stock + ?,
    version = version + 1
where dish_id = ?
  and available_stock >= ?
```

Confirm locked stock with:

```sql
update dish_stock
set locked_stock = locked_stock - ?,
    version = version + 1
where dish_id = ?
  and locked_stock >= ?
```

Release locked stock with:

```sql
update dish_stock
set available_stock = available_stock + ?,
    locked_stock = locked_stock - ?,
    version = version + 1
where dish_id = ?
  and locked_stock >= ?
```

If an update affects zero rows, the service should throw a business error and the surrounding transaction should roll back.

### Decision: Write Stock Records With Before/After Values

Add `stock_record`:

```text
id
dish_id
order_id
change_type
change_quantity
available_before
available_after
locked_before
locked_after
operator_id
remark
create_time
```

Initial change types:

```text
SET      admin sets available stock
LOCK     order submit locks stock
CONFIRM  mock payment confirms locked stock consumption
RELEASE  pending order cancellation releases locked stock
```

`SET.change_quantity` means `available_after - available_before`, so it can be positive or negative. For `LOCK`, `CONFIRM`, and `RELEASE`, `change_quantity` is the order-driven quantity.

`operator_id` rules:

```text
SET      current admin user ID
LOCK     submitting user ID
CONFIRM  current pay operator ID, or a system/null operator in future system-triggered flows
RELEASE  current cancel operator ID, or a system/null operator in future timeout-cancel flows
```

Implementation should read the stock row with `SELECT ... FOR UPDATE` inside the same transaction, execute the conditional update, then write the stock record with accurate before/after values. The conditional update remains the correctness guard, and the row lock makes audit values reliable.

Recommended indexes:

```text
idx_stock_record_dish_id
idx_stock_record_order_id
idx_stock_record_create_time
```

Alternative considered: store only current stock. That would prevent oversell but make inventory changes hard to debug and explain.

### Decision: Integrate With Existing Order Idempotency

Stock locking should happen only for the first successful submit that creates the order. When an idempotent retry returns an existing order ID, it must not lock stock again.

Recommended submit order:

```text
load/refresh cart
compute request_hash
insert PROCESSING idempotency record
create pending-payment order main record to obtain order_id
lock stock and write LOCK records with order_id
create order details
clear cart
mark idempotency SUCCEEDED
```

If any step fails, the transaction rolls back and no stock lock should persist.

The service should aggregate stock quantities by `dishId` and process stock updates in ascending `dishId` order for submit lock, payment confirmation, and cancellation release. This avoids duplicate stock operations for the same dish and reduces deadlock risk for multi-dish orders.

### Decision: Integrate With Order Status Transitions

Payment and cancellation already use order status checks, database conditional updates, and Redis order status locks. Stock side effects should be inside the same transaction:

```text
pay:
  PENDING_PAYMENT -> PAID
  confirm locked stock
  write CONFIRM records

cancel:
  PENDING_PAYMENT -> CANCELLED
  release locked stock
  write RELEASE records
```

The stock side effect must run only after the corresponding order status conditional update affects one row. If the order status update affects zero rows, the service must not confirm/release stock and must not write stock records. This prevents duplicate pay or duplicate cancel requests from changing stock twice.

No stock change is needed for accept, delivery, completion, refund start, or refund completion in this change.

## Risks / Trade-offs

- [Risk] Stock row locks can increase contention for hot dishes. -> Mitigation: keep transactions short, aggregate by dish ID, process in stable dish ID order, and continue using conditional updates as the correctness guard.
- [Risk] Admin setting available stock while orders are pending may surprise users. -> Mitigation: admin set only changes available stock and leaves locked stock intact.
- [Risk] Payment confirmation may fail if locked stock is inconsistent. -> Mitigation: throw a business error and roll back payment status change so order/payment/stock stay aligned.
- [Risk] Adding stock records increases implementation size. -> Mitigation: keep record types small and tied only to four operations: SET, LOCK, CONFIRM, RELEASE.

## Migration Plan

1. Add `dish_stock` and `stock_record` DDL.
2. Add stock entities, enums/constants, mappers, XML, service, and optional VO/DTO objects.
3. Add administrator stock query/set endpoints.
4. Integrate stock locking into first successful order submission.
5. Integrate stock confirmation into mock payment.
6. Integrate stock release into pending-payment cancellation.
7. Add unit/service tests and API regression steps.
8. Update `docs/API_TEST.md` and `docs/PROJECT_CONTEXT.md`.

Rollback strategy: remove stock integration from order submit/pay/cancel first, then ignore or drop `dish_stock` and `stock_record` in local development.

## Open Questions

- None. The agreed first version uses administrator-managed available stock, MySQL row locks plus conditional updates, and stock records.
