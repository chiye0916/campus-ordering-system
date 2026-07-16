## Why

The project already has mock refund order states, but refund is still an administrator-only internal operation. Adding a user refund request workflow makes the after-sales flow more realistic while keeping the first version small and aligned with the existing order, payment, and inventory boundaries.

## What Changes

- Add a `refund_request` business record so a `USER` can request a refund for their own `PAID` or `ACCEPTED` order.
- Add a refund request status lifecycle: `PENDING_REVIEW`, `APPROVED`, `REJECTED`, and `COMPLETED`.
- Add user refund request APIs for creating a request, listing the current user's requests, and viewing refund request details.
- Add administrator refund review APIs for listing requests, approving requests, rejecting requests, and completing approved mock refunds.
- On approval, update the refund request and move the order from `PAID` or `ACCEPTED` to `REFUNDING` in one transaction.
- On rejection, update only the refund request; the order status remains unchanged.
- On completion, update the refund request and move the order from `REFUNDING` to `REFUNDED` in one transaction.
- Keep existing `PUT /order/{id}/refund/start` and `PUT /order/{id}/refund/complete` as `ADMIN` internal mock fallback APIs that do not bind to `refund_request`.
- Do not restore inventory for refunds in this version.
- Do not add real payment-provider refunds, partial refunds, image uploads, user cancellation, merchant review, platform arbitration, or order status history in this change.

## Capabilities

### New Capabilities

- `refund-request-flow`: User refund request creation, administrator review, request status lifecycle, visibility, and API behavior.

### Modified Capabilities

- `order-status`: Existing refund order transitions are now also driven by approved and completed refund requests, while legacy order-level mock refund APIs remain available as internal fallback operations.

## Impact

- Database: add `refund_request` table with unique constraints for `order_id` and `refund_no`.
- Backend: add refund DTOs, VO, entity, enum, mapper/XML, controller, service, and service implementation.
- Order flow: add transactional coordination between refund request status and order status for approve and complete operations.
- APIs: add `/refund/...` endpoints for users and administrators; keep existing order-level mock refund endpoints.
- Tests: add unit tests for request creation, permission boundaries, status validation, duplicate prevention, approve/reject/complete behavior, and inventory non-restoration; optionally add an integration test for the full user-to-admin refund path.
- Documentation: later update API docs and project context to describe `/refund/...` as the recommended business flow and existing `/order/{id}/refund/...` as internal fallback APIs.
