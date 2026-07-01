## 1. Order Status Model

- [x] 1.1 Add an `OrderStatus` enum or equivalent domain model with codes `1-8`, display labels, and lookup by code.
- [x] 1.2 Define legal transitions in the order status model for pay, cancel, accept, start delivery, complete, start refund, and complete refund.
- [x] 1.3 Document in code/tests that numeric status order is not lifecycle order and lifecycle decisions must use `OrderStatus` transition rules.
- [x] 1.4 Add focused unit tests for allowed transitions.
- [x] 1.5 Add focused unit tests for rejected illegal transitions and unknown status codes.
- [x] 1.6 Add focused unit tests that reject `PAID -> COMPLETED`, `DELIVERING -> REFUNDING`, and `COMPLETED -> REFUNDING`.

## 2. Service Transition Refactor

- [x] 2.1 Update `OrderService` to expose action-oriented methods for accept, start delivery, start refund, and complete refund.
- [x] 2.2 Refactor `OrderServiceImpl.pay`, `cancel`, and `complete` to use the shared order status model.
- [x] 2.3 Change `complete` behavior from `PAID -> COMPLETED` to `DELIVERING -> COMPLETED`.
- [x] 2.4 Add a shared internal transition helper that acquires the Redis order status lock, validates the state-machine transition, and executes the database conditional update.
- [x] 2.5 Release Redis order status locks after transaction completion when transaction synchronization is active, and otherwise release in finally.
- [x] 2.6 Preserve user ownership checks for pay and pending-order cancellation.
- [x] 2.7 Preserve administrator checks for complete and add administrator checks for accept, start delivery, start refund, and complete refund.
- [x] 2.8 Treat `ADMIN` as the first-version maximum-permission demo operator for merchant, delivery, and mock refund actions.
- [x] 2.9 Keep user refund requests out of scope; do not add user-facing refund request APIs in this change.

## 3. Persistence Updates

- [x] 3.1 Add generic mapper support for status-only conditional updates from expected old status to new status.
- [x] 3.2 Keep existing timestamp updates for payment, cancellation, and completion.
- [x] 3.3 Ensure accept, delivery, refunding, and refunded transitions use the generic status-only database status-condition guard.
- [x] 3.4 Verify existing order pagination and detail queries return the expanded status values without breaking response shape.

## 4. API Updates

- [x] 4.1 Add administrator endpoint `PUT /order/{id}/accept`.
- [x] 4.2 Add administrator endpoint `PUT /order/{id}/delivery/start`.
- [x] 4.3 Adjust completion behavior so completion requires delivering status.
- [x] 4.4 Add administrator endpoint `PUT /order/{id}/refund/start`.
- [x] 4.5 Add administrator endpoint `PUT /order/{id}/refund/complete`.
- [x] 4.6 Use response/error wording that makes refund states clear as internal mock refunds.
- [x] 4.7 Update frontend/static status labels if current UI displays order status text.

## 5. Verification And Documentation

- [x] 5.1 Run `./mvnw test` and fix compile or unit test failures.
- [x] 5.2 Manually verify pending payment -> paid -> accepted -> delivering -> completed through HTTP/API flow.
- [x] 5.3 Manually verify pending payment -> cancelled through HTTP/API flow.
- [x] 5.4 Manually verify paid -> refunding -> refunded through HTTP/API flow.
- [x] 5.5 Manually verify accepted -> refunding -> refunded through HTTP/API flow.
- [x] 5.6 Manually verify illegal transitions return business errors and do not update order status.
- [x] 5.7 Manually verify paid orders can no longer be completed directly.
- [x] 5.8 Update `docs/API_TEST.md` with new status codes, endpoint paths, mock refund wording, and order transition verification steps.
- [x] 5.9 Update `docs/PROJECT_CONTEXT.md` with the expanded order status lifecycle and the current `ADMIN` super-operator interpretation.
