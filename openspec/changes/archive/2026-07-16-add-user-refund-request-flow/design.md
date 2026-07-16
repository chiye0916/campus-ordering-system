## Context

The order system already supports mock refund order states: `PAID` and `ACCEPTED` can transition to `REFUNDING`, and `REFUNDING` can transition to `REFUNDED`. Today those transitions are exposed only through administrator order-level mock refund endpoints:

- `PUT /order/{id}/refund/start`
- `PUT /order/{id}/refund/complete`

This change adds a business refund request workflow around those order states. The recommended flow becomes:

```text
USER creates refund request
  -> ADMIN approves request
  -> order enters REFUNDING
  -> ADMIN completes mock refund
  -> order enters REFUNDED
```

The project constraints remain unchanged: Spring MVC controllers call services, services own transactions and business rules, persistence uses MyBatis XML, current user data comes from JWT/BaseContext, and all APIs return `Result<T>`.

## Goals / Non-Goals

**Goals:**

- Let a `USER` request a refund for their own `PAID` or `ACCEPTED` order.
- Let an `ADMIN` list, approve, reject, and complete refund requests.
- Store refund requests in a new `refund_request` table with a business `refund_no`.
- Prevent duplicate refund requests for the same order with a unique order constraint.
- Keep refund request state and order state synchronized for approve and complete operations in one transaction.
- Preserve existing order-level mock refund endpoints as `ADMIN` internal fallback operations.
- Prevent existing order-level mock refund endpoints from bypassing an existing `refund_request`.
- Keep stock unchanged during refund request approval and completion.

**Non-Goals:**

- No real payment-provider refund integration.
- No partial refund.
- No image upload or evidence attachment.
- No user cancellation of refund requests.
- No merchant review or platform arbitration.
- No inventory restoration or stock record writing for refunds.
- No order status history table in this change.
- No removal or forced migration of existing order-level mock refund endpoints.

## Decisions

### Decision: Introduce a dedicated `refund_request` table

Create `refund_request` with:

```text
id
refund_no
order_id
user_id
order_number
amount
reason
status
reject_reason
reviewer_id
review_time
complete_time
create_time
update_time
```

Add unique constraints on `order_id` and `refund_no`.

Rationale: refund requests are business records, not just order status changes. Keeping them separate preserves request reason, review result, reviewer, and timestamps without overloading `orders`.

Alternative considered: store refund fields directly on `orders`. This was rejected because it would mix a request workflow into the order aggregate and make later audit/history work harder.

### Decision: Generate refund number and amount snapshot on the server

The client supplies only the target `orderId` and refund `reason` when creating a request. The server generates `refund_no`, copies `orders.amount` into `refund_request.amount`, and copies `orders.number` into `refund_request.order_number`.

Rationale: the first version does not support partial refunds, so clients must not choose the refund amount. The refund request should preserve the order amount snapshot at request creation time.

Alternative considered: allow clients to pass refund amount. This was rejected because partial refunds are out of scope and accepting client-provided amounts would create avoidable trust and validation complexity.

### Decision: One order can have at most one refund request

Use `unique(order_id)` and reject any later request for the same order, including after `REJECTED` or `COMPLETED`.

Rationale: the first version should avoid repeat application, appeal, and resubmission rules. A single request per order is easier to explain, test, and enforce. `REJECTED` is terminal in v1; the same order cannot submit another refund request after rejection.

Alternative considered: allow another request after terminal statuses. This was rejected for v1 because it requires additional business rules such as appeal limits and rejection reasons history.

### Decision: Refund request status is separate from order status

Refund request statuses:

```text
PENDING_REVIEW
APPROVED
REJECTED
COMPLETED
```

Order statuses remain unchanged:

```text
PAID / ACCEPTED -> REFUNDING -> REFUNDED
```

Approval updates both records:

```text
refund_request: PENDING_REVIEW -> APPROVED
orders: PAID or ACCEPTED -> REFUNDING
```

Completion updates both records:

```text
refund_request: APPROVED -> COMPLETED
orders: REFUNDING -> REFUNDED
```

Rejection updates only the refund request:

```text
refund_request: PENDING_REVIEW -> REJECTED
orders: unchanged
```

Approval and completion must check both conditional update counts. If either update affects zero rows, the service throws `BusinessException` and the transaction rolls back. Approval writes `reviewer_id`, `review_time`, and `update_time`; rejection writes `reviewer_id`, `review_time`, `reject_reason`, and `update_time`; completion writes `complete_time` and `update_time`.

Rationale: request state describes the review workflow, while order state describes the order lifecycle. Both need to move together only when business approval affects order lifecycle.

### Decision: `RefundService` coordinates order status directly

Do not call existing public `OrderService.startRefund(id)` or `OrderService.completeRefund(id)` from the refund request flow. Instead, `RefundService` performs conditional `orders` updates in the same transaction as refund request updates.

Rationale: the existing order-level methods do not bind to a `refund_request`. Calling them would risk the request and order states drifting apart. Direct coordination keeps the approve/complete transaction explicit.

Alternative considered: reuse the existing order-level service methods. This was rejected for v1 because those methods are fallback operations and do not know about refund request records.

### Decision: Keep old order-level refund endpoints as fallback APIs

Leave these endpoints available:

- `PUT /order/{id}/refund/start`
- `PUT /order/{id}/refund/complete`

They remain `ADMIN` internal mock fallback APIs and do not create, require, or update `refund_request`.

If a `refund_request` already exists for the target order, these legacy endpoints must reject the operation and instruct the administrator to use the `/refund/...` workflow.

Rationale: this avoids a breaking change and keeps existing manual testing behavior available while preventing the fallback APIs from desynchronizing an existing refund request from the order status. The recommended business flow will be documented as `/refund/...`.

### Decision: Refunds do not restore inventory

Do not change `dish_stock` and do not write `stock_record` for refund approval or completion.

Rationale: stock is already confirmed on successful payment. In a campus ordering scenario, refunded food is not assumed to return to sellable inventory. Refund is modeled as an after-sales/financial action, not inventory release.

### Request flow

Create request:

```text
POST /refund/request
  -> RefundController
  -> RefundService.createRequest
  -> OrdersMapper select order
  -> server generates refund_no and amount snapshot
  -> RefundRequestMapper insert refund_request
  -> Result<Long> refund request id
```

Approve request:

```text
PUT /refund/{id}/approve
  -> RefundController
  -> RefundService.approve
  -> RefundRequestMapper select request
  -> OrdersMapper conditional update PAID/ACCEPTED -> REFUNDING
  -> RefundRequestMapper conditional update PENDING_REVIEW -> APPROVED
  -> verify both update counts are 1
  -> Result<Void>
```

Reject request:

```text
PUT /refund/{id}/reject
  -> RefundController
  -> RefundService.reject
  -> RefundRequestMapper conditional update PENDING_REVIEW -> REJECTED with reviewer and reason
  -> Result<Void>
```

Complete request:

```text
PUT /refund/{id}/complete
  -> RefundController
  -> RefundService.complete
  -> RefundRequestMapper select request
  -> OrdersMapper conditional update REFUNDING -> REFUNDED
  -> RefundRequestMapper conditional update APPROVED -> COMPLETED
  -> verify both update counts are 1
  -> Result<Void>
```

## Risks / Trade-offs

- [Risk] Existing order-level fallback endpoints can move orders to `REFUNDING` while a refund request exists.  
  Mitigation: reject fallback endpoint operations when a `refund_request` already exists for the order, document them as internal fallback APIs, and make `/refund/...` the recommended business flow.

- [Risk] Approve or complete can partially update records if not transactional.  
  Mitigation: make approve and complete service methods transactional and use conditional updates for both order and refund request status.

- [Risk] Duplicate requests may race.  
  Mitigation: enforce `unique(order_id)` at the database level and translate duplicate key failures into a business error.

- [Risk] Not allowing another request after rejection is stricter than some real systems.  
  Mitigation: document it as a v1 scope choice; a future change can add appeal or resubmission rules.

- [Risk] Not restoring stock may look surprising for some products.  
  Mitigation: keep this as a documented domain rule for campus ordering food items and avoid stock side effects in tests.
