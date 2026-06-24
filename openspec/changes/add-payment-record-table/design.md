## Context

The project currently supports mock order payment through `PUT /order/{id}/pay`. The service verifies that the order belongs to the current user, checks that the order is pending payment, and updates the `orders` row from status `1` to `2` with a status condition in SQL. This protects the final order state, but the payment attempt itself is not persisted.

This change introduces a payment record data model so the mock payment flow leaves an auditable payment trail. The design stays within the current stack: Spring MVC, Service layer business logic, MyBatis XML, MySQL, and existing JWT/BaseContext user identity.

Request flow after this change:

```text
PUT /order/{id}/pay
  -> OrderController.pay
  -> OrderServiceImpl.pay
  -> OrdersMapper.selectById
  -> PaymentRecordMapper insert/update
  -> OrdersMapper.updateToPaidById where status = 1
  -> PaymentRecordMapper update success status
  -> Result<OrderPayVO>
```

## Goals / Non-Goals

**Goals:**

- Add a `payment_record` table that records payment attempts/results for orders.
- Persist one mock payment record when a pending order is paid successfully.
- Keep the existing `PUT /order/{id}/pay` API response shape.
- Preserve the existing order status condition update as the database-level consistency guard.
- Keep SQL in MyBatis XML and money fields as `BigDecimal`.
- Make the design ready for future callback/idempotency work without implementing that full payment system now.

**Non-Goals:**

- Do not integrate a real payment provider.
- Do not add external payment callback endpoints.
- Do not implement refunds or reconciliation jobs.
- Do not add Redis distributed locks in this change.
- Do not change frontend payment behavior beyond any optional display/testing needs.

## Decisions

### Decision: Add `payment_record` Instead Of Expanding `orders`

Payment process data will be stored in a separate `payment_record` table. The `orders` table remains responsible for the final business state of the order, while `payment_record` stores the payment attempt/process state.

Alternative considered: add payment fields directly to `orders`. This is simpler for the current mock flow, but it mixes order state with payment process details and becomes awkward once callbacks, retries, failures, and refunds are introduced.

Suggested table fields:

```text
id                bigint primary key
order_id          bigint not null
order_number      varchar(64) not null
user_id           bigint not null
amount            decimal(10,2) not null
pay_channel       varchar(32) not null
trade_no          varchar(64) not null
third_trade_no    varchar(128) null
status            tinyint not null
request_time      datetime not null
success_time      datetime null
callback_time     datetime null
failure_reason    varchar(255) null
create_time       datetime not null
update_time       datetime not null
```

Suggested statuses:

```text
1 = paying
2 = success
3 = failed
4 = closed
```

Suggested indexes:

```text
uk_payment_trade_no(trade_no)
idx_payment_order_id(order_id)
idx_payment_user_id(user_id)
idx_payment_status(status)
```

### Decision: Use `MOCK` Payment Channel For Current Flow

The current `pay()` method is a simulated payment, so new payment records will use `pay_channel = 'MOCK'`.

Alternative considered: leave channel nullable until real integration. A non-null channel is clearer for testing and makes future multi-channel logic easier to reason about.

### Decision: Create And Complete A Record Inside The Mock Payment Flow

For the current demo, `pay()` should create a payment record in a paying state, perform the existing order status update, then mark the payment record as success if the order update succeeds.

If the order status update returns zero rows, the service should not mark the payment record successful. The implementation can either mark it failed/closed or rely on the surrounding transaction to roll back the record. Because the current payment is synchronous and mock-only, a single transaction around the record insert and order update is acceptable.

### Decision: Keep Database Status Condition Updates

The SQL guard `where id = ? and status = 1` remains required for `pay()`. Payment records add auditability, but they do not replace state transition protection.

This also keeps the later Redis distributed lock change clean: Redis can protect the concurrent entrance to order status operations, while MySQL remains the final consistency guard.

### Decision: Defer Successful-Payment Uniqueness Enforcement

The first implementation will not enforce "only one successful payment record per order" with a database unique key. It will enforce `trade_no` uniqueness and rely on the existing conditional order status update to prevent the order from being paid twice.

Alternative considered: add a MySQL-friendly uniqueness mechanism such as a nullable `success_order_id` field with a unique index. That is useful for a stronger idempotency design, but it adds complexity before the project has real callbacks or Redis locking. This will be reconsidered in the later idempotency/Redis distributed lock change.

### Decision: Roll Back Failed Mock Payment Attempts

The first implementation will not persist failed mock payment records. Business validation failures, rejected non-pending orders, and conditional order status update failures should not leave a successful payment record; the mock payment transaction should roll back instead.

Alternative considered: persist `FAILED` payment records immediately. That better matches a real payment provider integration, but the current mock flow has no real external failure reason to audit. Persisting failed attempts will be more useful when callback and provider error handling are introduced.

## Risks / Trade-offs

- [Risk] A payment record could be created without an order status update if transaction boundaries are wrong. → Mitigation: keep payment record insert/update and order status update in the same service transaction for this mock flow.
- [Risk] A future real callback flow will not match the synchronous mock payment exactly. → Mitigation: include callback-oriented fields now, but keep callback behavior out of this change.
- [Risk] Duplicate successful payment records could appear under repeated requests if uniqueness is not considered. → Mitigation: create a unique internal `trade_no` now and keep order status condition update; later idempotency can add stricter order/channel/status rules.
- [Risk] The table adds fields not immediately used by the mock flow. → Mitigation: fields are limited to standard payment lifecycle data needed by the next learning steps.

## Migration Plan

1. Add the `payment_record` table to the local database migration/schema notes.
2. Add entity and mapper/XML files for inserting and updating payment records.
3. Wrap the mock payment record creation, order status update, and payment record success update in the service transaction.
4. Run Maven tests and API regression checks.
5. Verify database rows after calling `PUT /order/{id}/pay`.

Rollback strategy: remove the payment record service integration first, then drop or ignore the `payment_record` table if the change is abandoned.

## Open Questions

- None for this change.
