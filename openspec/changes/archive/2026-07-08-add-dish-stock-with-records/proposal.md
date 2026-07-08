## Why

The order flow now has explicit status transitions and submit idempotency, but it still does not control dish inventory. Adding stock locking and stock records makes the core trading chain more reliable and auditable before RabbitMQ timeout cancellation or Redis/Lua stock optimization is introduced.

## What Changes

- Add a dedicated `dish_stock` table for available stock, locked stock, and version.
- Add a dedicated `stock_record` table for every stock-changing operation.
- Add administrator APIs to query and set dish stock separately from dish creation/update.
- Lock stock during order submission by moving quantity from available stock to locked stock.
- Confirm locked stock consumption when mock payment succeeds.
- Release locked stock when a pending-payment order is cancelled.
- Write stock records for administrator set stock, order submit lock, payment confirm, and pending-order cancellation release.
- Keep stock changes in the same transaction as their related order/payment/cancel status changes.
- Keep MySQL conditional updates as the first implementation; do not add Redis Lua stock pre-deduction in this change.
- Out of scope: RabbitMQ timeout cancellation, Redis stock cache/Lua scripts, multi-store/multi-warehouse stock, stock warning rules, and complex stock audit approval.

## Capabilities

### New Capabilities
- `dish-stock`: Defines dish stock storage, administrator stock query/set behavior, stock locking, release, and confirmation rules.
- `stock-record`: Defines auditable stock change records for manual stock setting and order-driven stock changes.

### Modified Capabilities
- `order-submit-idempotency`: Order submission must lock stock only for the first successful submit, and duplicate idempotent retries must not lock stock again.
- `order-status`: Payment and cancellation transitions now have stock side effects: payment confirms locked stock consumption, and pending-payment cancellation releases locked stock.

## Impact

- Affected APIs: new administrator stock query/set endpoints; existing `POST /order/submit`, `PUT /order/{id}/pay`, and `PUT /order/{id}/cancel`.
- Affected database: add `dish_stock` and `stock_record` tables.
- Affected backend code: new stock entity/mapper/service/controller or controller methods, `OrderServiceImpl`, order submit transaction, payment flow, cancellation flow, tests, and API docs.
- Affected frontend/docs: stock setup must happen before a dish can be ordered; API regression docs need stock initialization, stock locking, payment confirmation, and cancellation release checks.
