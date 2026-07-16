## 1. Data Model

- [x] 1.1 Add `refund_request` table to `sql/schema.sql` with `refund_no`, order snapshot fields, request status fields, review fields, timestamps, `unique(order_id)`, and `unique(refund_no)`.
- [x] 1.2 Add `RefundRequest` entity and `RefundRequestStatus` enum with `PENDING_REVIEW`, `APPROVED`, `REJECTED`, and `COMPLETED`.
- [x] 1.3 Add refund request DTOs for create, reject, and page query inputs; create DTO must accept only `orderId` and `reason`, not `amount` or `refundNo`.
- [x] 1.4 Add refund request VO(s) for list/detail responses.
- [x] 1.5 Add `RefundNoUtil` or equivalent server-side refund number generator and rely on `unique(refund_no)` as a database safeguard.

## 2. Persistence

- [x] 2.1 Add `RefundRequestMapper` interface.
- [x] 2.2 Add `RefundRequestMapper.xml` with insert, select by id, select by order id, conditional status updates, count page, and select page SQL.
- [x] 2.3 Add any missing `OrdersMapper` conditional update methods needed for `PAID/ACCEPTED -> REFUNDING` and `REFUNDING -> REFUNDED` from the refund request flow.

## 3. Service Layer

- [x] 3.1 Add `RefundService` interface and implementation.
- [x] 3.2 Implement user refund request creation with `USER` role check, ownership check, `PAID/ACCEPTED` eligibility, non-blank reason validation, duplicate request prevention, server-generated refund number, order amount snapshot, and no stock side effects.
- [x] 3.3 Implement user and admin refund request list/detail visibility rules with `page`, `pageSize`, and optional `status` filtering.
- [x] 3.4 Implement admin approve with `ADMIN` role check and one transaction updating `refund_request PENDING_REVIEW -> APPROVED` and `orders PAID/ACCEPTED -> REFUNDING`; check both conditional update counts and roll back if either is zero.
- [x] 3.5 Implement admin reject with `ADMIN` role check, non-blank reject reason validation, `reviewer_id`, `review_time`, `reject_reason`, `update_time`, and no order status change.
- [x] 3.6 Implement admin complete with `ADMIN` role check and one transaction updating `refund_request APPROVED -> COMPLETED` and `orders REFUNDING -> REFUNDED`; check both conditional update counts and roll back if either is zero.
- [x] 3.7 Translate duplicate key and conditional update failures into clear `BusinessException` responses.
- [x] 3.8 Add legacy order-level refund fallback protection so `/order/{id}/refund/start` and `/order/{id}/refund/complete` reject when the order already has a `refund_request`.

## 4. Controller And API

- [x] 4.1 Add `RefundController` with `POST /refund/request`, `GET /refund/my/page`, `GET /refund/{id}`, `GET /refund/page`, `PUT /refund/{id}/approve`, `PUT /refund/{id}/reject`, and `PUT /refund/{id}/complete`.
- [x] 4.2 Use validation annotations on request DTOs and keep all responses wrapped in `Result<T>`.
- [x] 4.3 Keep existing `PUT /order/{id}/refund/start` and `PUT /order/{id}/refund/complete` available as `ADMIN` internal fallback APIs for orders without `refund_request`; reject them when a refund request exists.

## 5. Tests

- [x] 5.1 Add unit tests for successful refund request creation from `PAID` and `ACCEPTED` own orders.
- [x] 5.2 Add unit tests rejecting another user's order, non-`USER` creation, ineligible order statuses, blank reason, client-provided amount/refund number assumptions, and duplicate refund requests.
- [x] 5.3 Add unit tests for admin approve, reject, and complete success paths.
- [x] 5.4 Add unit tests for repeated approve/reject/complete and changed order status conditional update failures.
- [x] 5.5 Add unit tests proving `MERCHANT`, `DELIVERY`, and `SYSTEM` cannot approve, reject, or complete refund requests.
- [x] 5.6 Add tests proving refund request creation, approval, and completion do not change `dish_stock` or write `stock_record`.
- [x] 5.7 Add tests proving legacy order-level refund fallback endpoints reject when a refund request exists for the order.
- [x] 5.8 Add or update controller tests for request validation and permission boundaries.
- [x] 5.9 Add an integration test for `USER request -> ADMIN approve -> ADMIN complete` if the existing Testcontainers suite remains practical for this flow.

## 6. Documentation And Verification

- [x] 6.1 Update `docs/API_TEST.md` with the recommended `/refund/...` user refund request flow and note the legacy order-level refund APIs as internal fallback APIs.
- [x] 6.2 Update `docs/PROJECT_CONTEXT.md` after implementation to include the refund request table, APIs, and rules.
- [x] 6.3 Run `./mvnw test`.
- [x] 6.4 Run `./mvnw verify -Pintegration-test` when Docker is available or document why it was skipped.
- [x] 6.5 Run `openspec validate add-user-refund-request-flow --strict`.
