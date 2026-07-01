## Context

The project currently stores order status as integer values on the `orders` table and checks valid transitions directly inside `OrderServiceImpl`. The implemented states are:

```text
1 pending payment
2 paid
3 completed
4 cancelled
```

The current reliable pieces should be preserved:

- `pay`, `cancel`, and `complete` run inside service-layer business methods.
- Status updates use MyBatis XML with database status conditions as the final guard.
- Redis order status locks protect status transition logic.
- `PermissionChecker.requireAdmin()` protects administrator-only actions.
- Current user identity comes from `BaseContext`, not from frontend input.

This change expands the lifecycle before adding inventory locking, submit idempotency, payment callback idempotency, or RabbitMQ timeout cancellation.

`ADMIN` in this change means the first-version super operator for learning/demo purposes. It temporarily performs merchant, delivery, refund, and platform operations because the project does not yet have store ownership, merchant staff, delivery staff, or fine-grained RBAC data models.

## Goals / Non-Goals

**Goals:**

- Expand order statuses to cover payment, merchant acceptance, delivery, completion, cancellation, and refund.
- Introduce an explicit order state model so legal transitions are defined in one place.
- Add administrator operations for accepting orders, starting delivery, starting refund, and completing refund.
- Define refunding/refunded as internal mock refund states only.
- Preserve existing user payment and pending-order cancellation behavior.
- Change completion from the old direct `PAID -> COMPLETED` behavior to the new delivery-based `DELIVERING -> COMPLETED` behavior.
- Apply Redis order status locks and database status-condition updates to every order transition.
- Keep the current JWT + HandlerInterceptor + `PermissionChecker` authorization style.
- Add focused unit tests for transition rules and regression checks for service/API behavior.

**Non-Goals:**

- Do not add stock locking or inventory tables in this change.
- Do not add submit idempotency.
- Do not implement real payment provider callbacks or refund provider integration.
- Do not implement user refund requests; a later `refund-request` change can add request/review states such as `REFUND_REQUESTED`.
- Do not add RabbitMQ timeout cancellation.
- Do not introduce Spring Security or a full RBAC model.
- Do not add a separate order status history table.

## Decisions

### Decision: Preserve Existing Status Codes And Append New Ones

The status codes will be:

```text
1 PENDING_PAYMENT
2 PAID
3 COMPLETED
4 CANCELLED
5 ACCEPTED
6 DELIVERING
7 REFUNDING
8 REFUNDED
```

Existing data and API expectations already use `1-4`, so the change appends new values instead of renumbering the lifecycle order.

Numeric status order is not lifecycle order. The business lifecycle is:

```text
1 PENDING_PAYMENT -> 2 PAID -> 5 ACCEPTED -> 6 DELIVERING -> 3 COMPLETED
```

Code and frontend logic must not infer lifecycle progress from numeric comparisons such as `status > 2`. All transition checks and progress decisions must use `OrderStatus` transition rules.

Alternative considered: reorder codes to match the ideal lifecycle. That would make the enum visually cleaner, but it risks confusing existing API tests, database rows, and frontend display logic.

### Decision: Model Transitions Explicitly

Create an `OrderStatus` enum or equivalent domain model with:

- numeric code
- display label
- legal next states
- helpers such as `canTransitionTo(...)` and `requireTransitionTo(...)`

The intended lifecycle is:

```text
PENDING_PAYMENT -> PAID -> ACCEPTED -> DELIVERING -> COMPLETED
       |
       v
   CANCELLED

PAID -> REFUNDING -> REFUNDED
ACCEPTED -> REFUNDING -> REFUNDED
```

First-version refund rules intentionally allow admin-triggered mock refunds from `PAID` and `ACCEPTED`. Refunds from `DELIVERING` or `COMPLETED`, and user-submitted refund requests, are out of scope and can be designed later as after-sales behavior.

The transition table is:

| Action | Actor | Old status | New status |
| --- | --- | --- | --- |
| pay | Order owner | `PENDING_PAYMENT` | `PAID` |
| cancel | Order owner | `PENDING_PAYMENT` | `CANCELLED` |
| accept | `ADMIN` | `PAID` | `ACCEPTED` |
| startDelivery | `ADMIN` | `ACCEPTED` | `DELIVERING` |
| complete | `ADMIN` | `DELIVERING` | `COMPLETED` |
| startRefund | `ADMIN` | `PAID` or `ACCEPTED` | `REFUNDING` |
| completeRefund | `ADMIN` | `REFUNDING` | `REFUNDED` |

Alternative considered: keep transition checks as `if` statements in `OrderServiceImpl`. That is simpler for a small demo, but it becomes hard to reason about once the lifecycle grows to eight statuses.

### Decision: Use Action-Oriented Service Methods

The service should expose business actions rather than a generic "set status" method:

```text
pay(orderId)
cancel(orderId)
accept(orderId)
startDelivery(orderId)
complete(orderId)
startRefund(orderId)
completeRefund(orderId)
```

Each action maps to one expected old status and one new status. This avoids letting callers pick arbitrary target states.

The controller endpoints will be:

```text
PUT /order/{id}/pay
PUT /order/{id}/cancel
PUT /order/{id}/accept
PUT /order/{id}/delivery/start
PUT /order/{id}/complete
PUT /order/{id}/refund/start
PUT /order/{id}/refund/complete
```

Alternative considered: add a generic `changeStatus(orderId, targetStatus)` endpoint. That would be shorter, but it would push too much business meaning into request parameters and make illegal operations easier to call.

### Decision: Keep ADMIN As First-Version Super Operator

First-version authorization:

```text
USER: pay own order, cancel own pending-payment order
ADMIN: accept, start delivery, complete, start refund, complete refund
```

The existing `PermissionChecker.requireAdmin()` remains sufficient for this change. `ADMIN` is not modeled as a real merchant yet; it is the maximum-permission operator for the current demo. Later work can split `ADMIN` into `MERCHANT`, `EMPLOYEE`, `DELIVERY`, `CUSTOMER`, and platform `ADMIN` when store ownership and Spring Security/RBAC are introduced.

User refund requests are intentionally excluded. A later flow can add:

```text
PAID / ACCEPTED -> REFUND_REQUESTED -> REFUNDING / rejected
```

### Decision: Reuse Redis Lock And Database Conditions For Every Transition

Every transition should follow the same service flow:

```text
Controller action
  -> authorization check
  -> OrderService action
  -> acquire lock: lock:order:status:{orderId}
  -> load order and validate visibility/ownership if needed
  -> validate state-machine transition
  -> update orders where id = ? and status = expectedOldStatus
  -> release lock after transaction completion when transaction synchronization is active
  -> otherwise release lock in finally
  -> Result return
```

The Redis lock reduces concurrent business execution. The database condition remains the final consistency guard. The implementation should prefer `TransactionSynchronizationManager.registerSynchronization(...)` for unlock timing when the transition runs in a Spring transaction.

### Decision: Keep Database Schema Minimal

No new table is required. The existing `orders.status` column can store the expanded values. The SQL updates should keep setting the relevant timestamp fields where available:

- `pay_time` for paid
- `cancel_time` for cancelled
- `complete_time` for completed

For accepted, delivering, refunding, and refunded, this change can either reuse status-only updates or add timestamp columns later. To keep this change focused, no new timestamp columns are required.

Mapper updates should keep the current dedicated timestamp methods for payment, cancellation, and completion, and add a generic status-only conditional update for accept, delivery, refunding, and refunded transitions.

## Risks / Trade-offs

- [Risk] Without timestamp columns for accepted/delivering/refunding/refunded, audit detail is limited. -> Mitigation: keep this change focused on state correctness; add status history or timestamps in a later observability/audit change.
- [Risk] Adding refund states without real provider integration may look incomplete. -> Mitigation: define refunding/refunded as internal mock business states only; provider callbacks and user refund requests are explicitly out of scope. API messages should use wording such as "mock refund started" or "mock refund completed" rather than implying a third-party refund succeeded.
- [Risk] Frontend display may not recognize new status codes. -> Mitigation: update static frontend status labels or document API behavior as part of implementation.
- [Risk] Multiple action methods may duplicate lock/update code. -> Mitigation: extract a small internal transition helper while keeping public methods action-oriented.
- [Risk] Existing API tests may assume paid orders can be completed directly. -> Mitigation: update tests and docs to require `pay -> accept -> startDelivery -> complete`.

## Migration Plan

1. Add the order status model and unit tests for legal/illegal transitions.
2. Update `OrderService` and `OrderServiceImpl` with action-oriented methods.
3. Add controller endpoints for administrator transitions.
4. Add mapper methods/XML status-condition updates for new transitions, or a parameterized conditional update.
5. Update frontend/API documentation status labels where needed.
6. Run `./mvnw test` and targeted API regression for order payment, cancellation, acceptance, delivery, completion, and refund.

Rollback strategy: remove new endpoints and service methods, then keep the existing four status codes. Existing rows with new statuses would need manual conversion only if data was created during testing.

## Open Questions

- None. The first version will use `ADMIN` as the maximum-permission demo operator for accepted, delivering, refunding, and refunded operations.
