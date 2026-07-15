## 1. Role Model And Context

- [x] 1.1 Add a `Role` enum with `USER`, `MERCHANT`, `DELIVERY`, `ADMIN`, and `SYSTEM`.
- [x] 1.2 Add parsing helpers that reject unsupported role values with an authentication/permission error and never default unknown values to `USER`, `MERCHANT`, `DELIVERY`, `ADMIN`, or `SYSTEM`.
- [x] 1.3 Update constants or replace role string usage with `Role` where appropriate while keeping database storage as `VARCHAR`.
- [x] 1.4 Ensure JWT generation and parsing supports `MERCHANT` and `DELIVERY` roles.
- [x] 1.5 Ensure the interceptor stores the parsed current role in `BaseContext` and still clears context after each request.
- [x] 1.6 Confirm `SYSTEM` remains invalid for normal login.

## 2. PermissionChecker Refactor

- [x] 2.1 Add centralized helpers such as `requireRole`, `requireAnyRole`, `requireUser`, `requireMerchantOrAdmin`, `requireDeliveryOrAdmin`, `requireAdmin`, and owner-or-admin support.
- [x] 2.2 Ensure owner-or-admin authorization is used only for read/admin visibility cases and not for customer mutations such as submit, pay, or self-cancel.
- [x] 2.3 Ensure normal HTTP permission helpers do not grant endpoint access to `SYSTEM`.
- [x] 2.4 Replace direct role string comparisons in controllers/services with `Role` and `PermissionChecker` helpers.
- [x] 2.5 Preserve existing business exception style and permission error response shape.
- [x] 2.6 Add focused unit tests for allowed and denied role combinations, including unsupported role handling.

## 3. Customer Flow Authorization

- [x] 3.1 Restrict cart operations to `USER`.
- [x] 3.2 Restrict `POST /order/submit` to `USER`.
- [x] 3.3 Restrict `PUT /order/{id}/pay` to `USER` and keep existing owner/state checks.
- [x] 3.4 Restrict `PUT /order/{id}/cancel` customer cancel to `USER` and keep existing owner/state/stock-release behavior.
- [x] 3.5 Ensure `MERCHANT`, `DELIVERY`, `ADMIN`, and `SYSTEM` cannot use customer cart/order submit/pay/cancel flows.
- [x] 3.6 Keep `/dish/list` access behavior unchanged and avoid adding role-only restrictions to it.

## 4. Management And Fulfillment Authorization

- [x] 4.1 Allow `MERCHANT` and `ADMIN` to manage categories.
- [x] 4.2 Allow `MERCHANT` and `ADMIN` to create, update, page, and change status for dishes.
- [x] 4.3 Allow `MERCHANT` and `ADMIN` to read and set dish stock.
- [x] 4.4 Allow `MERCHANT` and `ADMIN` to accept paid orders.
- [x] 4.5 Allow `DELIVERY` and `ADMIN` to start delivery for accepted orders.
- [x] 4.6 Allow `DELIVERY` and `ADMIN` to complete delivering orders.
- [x] 4.7 Keep internal mock refund start and complete operations `ADMIN` only.
- [x] 4.8 Ensure unauthorized roles cannot perform management or fulfillment transitions and cannot change order status.
- [x] 4.9 Ensure `MERCHANT` refund-related detail visibility does not grant refund start or complete permissions.

## 5. Order Visibility

- [x] 5.1 Update order page visibility so `USER` sees only own orders in all statuses.
- [x] 5.2 Update order page visibility so `MERCHANT` sees only global `PAID` and `ACCEPTED` orders.
- [x] 5.3 Update order page visibility so `DELIVERY` sees only global `ACCEPTED` and `DELIVERING` orders.
- [x] 5.4 Update order page visibility so `ADMIN` sees all orders.
- [x] 5.5 Update order detail visibility so `USER` sees only own orders in all statuses.
- [x] 5.6 Update order detail visibility so `MERCHANT` sees global `PAID`, `ACCEPTED`, `DELIVERING`, `COMPLETED`, `REFUNDING`, and `REFUNDED` orders, but not `PENDING_PAYMENT` or `CANCELLED` orders.
- [x] 5.7 Update order detail visibility so `DELIVERY` sees global `ACCEPTED`, `DELIVERING`, and `COMPLETED` orders only.
- [x] 5.8 Update order detail visibility so `ADMIN` sees all orders.
- [x] 5.9 Ensure `SYSTEM` has no normal HTTP order list/detail visibility.

## 6. System Actor And Registration Boundaries

- [x] 6.1 Keep public registration creating only `USER` accounts.
- [x] 6.2 Ensure registration rejects role input if received and never persists `MERCHANT`, `DELIVERY`, `ADMIN`, or `SYSTEM` from public registration.
- [x] 6.3 Keep `system_timeout` as the `SYSTEM` audit subject for timeout stock release.
- [x] 6.4 Do not add employee-management APIs for creating merchant, delivery, or admin users.
- [x] 6.5 Document that local/test setup may create non-`USER` roles through SQL or test helpers.

## 7. Tests

- [x] 7.1 Add or update controller/service tests proving `USER` customer flows still work and non-`USER` roles are denied.
- [x] 7.2 Add or update tests proving `MERCHANT` can manage catalog/stock and accept orders while `USER` and `DELIVERY` cannot.
- [x] 7.3 Add or update tests proving `DELIVERY` can start delivery and complete orders while `USER` and `MERCHANT` cannot.
- [x] 7.4 Add or update tests proving `ADMIN` can perform management, fulfillment, and internal mock refund operations but cannot use customer flows.
- [x] 7.5 Add or update tests proving `SYSTEM` cannot log in and cannot use normal HTTP permissions.
- [x] 7.6 Add order visibility tests for `USER`, `MERCHANT`, `DELIVERY`, and `ADMIN`.
- [x] 7.7 Update Testcontainers integration helpers and integration tests where role setup or assertions are affected.
- [x] 7.8 Add tests proving public registration rejects role input such as `ADMIN`, `MERCHANT`, `DELIVERY`, or `SYSTEM`.
- [x] 7.9 Add tests proving unsupported role values fail closed and do not grant access or default to another role.

## 8. Documentation And Validation

- [x] 8.1 Update `docs/PROJECT_CONTEXT.md` with the new role boundaries and current stage status.
- [x] 8.2 Update `docs/API_TEST.md` with role setup SQL, login notes, permission examples, and order visibility expectations.
- [x] 8.3 Update any frontend/static role checks or documentation needed for `MERCHANT` and `DELIVERY` if affected by backend API testing.
- [x] 8.4 Run `openspec validate refine-roles-and-admin-permissions --strict`.
- [x] 8.5 Run `openspec validate --all --strict`.
- [x] 8.6 Run `./mvnw test`.
- [x] 8.7 Run `./mvnw verify -Pintegration-test` when Docker/Testcontainers access is available, or document the environment reason if it cannot run.
