## Why

The current order flow supports only pending payment, paid, completed, and cancelled states, with status checks embedded in service methods. The next trading-system upgrade needs an explicit order state model so payment, merchant handling, delivery, completion, cancellation, and refund behavior are predictable before inventory, idempotency, and RabbitMQ timeout cancellation are added.

## What Changes

- Introduce an explicit order status capability with eight statuses: pending payment, paid, accepted, delivering, completed, cancelled, refunding, and refunded.
- Add state-machine rules that define which transitions are legal and which roles can trigger them.
- Add administrator operations for accepting orders, starting delivery, initiating mock refund, and confirming mock refund.
- Keep user operations focused on paying and cancelling pending-payment orders in this change.
- Treat `ADMIN` as the first-version super operator for learning/demo purposes; later changes may split this into merchant, delivery, customer, and platform administrator roles.
- Keep user refund requests out of scope; this change supports only admin-triggered mock refunds from paid or accepted orders.
- Change existing completion behavior from `PAID -> COMPLETED` to `DELIVERING -> COMPLETED`.
- Preserve the existing Redis order status lock pattern and database status-condition updates for every status transition.
- Keep API responses using the existing `Result<T>` pattern and continue using MyBatis XML.
- Out of scope: stock locking, submit idempotency, real payment provider callbacks, user refund requests, RabbitMQ timeout cancellation, and full Spring Security/RBAC.

## Capabilities

### New Capabilities
- `order-status`: Defines order statuses, legal transitions, actor permissions, and persistence expectations for order state changes.

### Modified Capabilities
- `order-status-lock`: Extends Redis order status lock coverage from pay/cancel/complete to all order status transitions introduced by this change.

## Impact

- Affected APIs: existing `PUT /order/{id}/pay`, `PUT /order/{id}/cancel`, and `PUT /order/{id}/complete`; new administrator endpoints `PUT /order/{id}/accept`, `PUT /order/{id}/delivery/start`, `PUT /order/{id}/refund/start`, and `PUT /order/{id}/refund/complete`.
- Affected backend code: `OrderController`, `OrderService`, `OrderServiceImpl`, `OrdersMapper`, `OrdersMapper.xml`, order status constants/model, VO display fields if needed, and API test documentation.
- Affected database: `orders.status` values expand from `1-4` to `1-8`; no new table is required for this change.
- Affected verification: add focused unit tests for the state machine and run existing Maven/API regression checks.
