## Why

The current mock payment flow updates the order status directly, but it does not keep an auditable record of the payment attempt. Adding a payment record table prepares the project for later real payment callbacks, idempotency checks, reconciliation, and troubleshooting while keeping the current demo payment flow simple.

## What Changes

- Add a `payment_record` capability for recording payment attempts and successful mock payments.
- Add a database table design that stores order identity, user identity, amount, payment channel, internal trade number, optional third-party trade number, payment status, and payment timestamps.
- Integrate the mock `PUT /order/{id}/pay` flow with payment record creation/update so a successful mock payment leaves a corresponding payment record.
- Keep the existing order status transition behavior and database status condition update as the final consistency guard.
- Keep this change scoped to data model and mock payment recording only.
- Out of scope: real Alipay/WeChat integration, external payment callbacks, refunds, Redis distributed locks, and payment reconciliation jobs.

## Capabilities

### New Capabilities

- `payment-record`: Records payment attempts/results for orders and provides the foundation for future callback, idempotency, and reconciliation work.

### Modified Capabilities

- None.

## Impact

- Database: add `payment_record` table and indexes.
- Domain model: add a payment record entity and mapper/XML files using MyBatis XML.
- Service: update `OrderServiceImpl.pay()` to create/update a mock payment record around the existing order status transition.
- API behavior: no endpoint path changes; `PUT /order/{id}/pay` continues to return the existing payment result.
- Documentation/tests: update API regression notes to verify payment record persistence.
