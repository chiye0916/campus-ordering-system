## Why

The system currently treats `ADMIN` as a first-version super-operator for merchant, delivery, platform, and refund actions. The next step is to make role boundaries closer to a real ordering system while keeping the implementation lightweight and avoiding full RBAC, Spring Security, multi-store isolation, or delivery assignment.

## What Changes

- Add explicit login roles: `USER`, `MERCHANT`, `DELIVERY`, and `ADMIN`; keep `SYSTEM` as an internal actor role.
- Introduce a `Role` model and centralized permission helpers so controllers and services do not rely on scattered role strings; unsupported role values MUST fail closed instead of defaulting to any valid role.
- Keep `SYSTEM` as an internal audit actor, backed by the existing `system_timeout` user, and reject normal login or normal HTTP endpoint authorization for `SYSTEM`.
- Keep public registration limited to `USER`; users MUST NOT self-register as `MERCHANT`, `DELIVERY`, `ADMIN`, or `SYSTEM`, and registration requests that carry role input MUST be rejected rather than persisted as a privileged role.
- Restrict customer flows to `USER` only: cart operations, order submit, mock payment initiation, pending-payment self-cancel, and viewing own orders.
- Allow `/dish/list` to keep its existing authentication requirement, if any, without additional role restrictions.
- Allow `MERCHANT` and `ADMIN` to manage categories, dishes, dish stock, and merchant-side order acceptance.
- Allow `DELIVERY` and `ADMIN` to start delivery and complete delivering orders.
- Keep internal mock refund operations `ADMIN` only.
- Allow `MERCHANT` to view refund-related order detail statuses for lifecycle awareness only; refund start and complete operations remain `ADMIN` only.
- Refine order list visibility:
  - `USER` sees own orders in all statuses.
  - `MERCHANT` sees global `PAID` and `ACCEPTED` orders.
  - `DELIVERY` sees global `ACCEPTED` and `DELIVERING` orders.
  - `ADMIN` sees all orders.
- Refine order detail visibility:
  - `USER` sees own orders in all statuses.
  - `MERCHANT` sees global `PAID`, `ACCEPTED`, `DELIVERING`, `COMPLETED`, `REFUNDING`, and `REFUNDED` orders.
  - `DELIVERY` sees global `ACCEPTED`, `DELIVERING`, and `COMPLETED` orders.
  - `ADMIN` sees all orders.
- Do not add employee management APIs; test data and local setup may create non-`USER` roles through SQL or test helpers.
- Do not change order state-machine, payment, stock, timeout, or idempotency business semantics except for actor authorization.

## Capabilities

### New Capabilities

- `role-permissions`: Defines supported roles, public registration limits, JWT/BaseContext role handling, centralized permission helper behavior, API role boundaries, and order visibility rules.

### Modified Capabilities

- `order-status`: Refine actor permissions for order transitions from `ADMIN` super-operator semantics to `USER` / `MERCHANT` / `DELIVERY` / `ADMIN` / `SYSTEM` boundaries.

## Impact

- Affected code: `Constants`, role model/enums, `PermissionChecker`, JWT role parsing, `BaseContext` role access, controllers, order service visibility and transition authorization, tests, and documentation.
- Affected APIs: authorization outcomes for cart/order customer APIs, category/dish/stock management, order status transitions, and order list/detail visibility.
- Affected data: no table redesign; existing `user.role` string column can store `MERCHANT` and `DELIVERY`. `system_timeout` remains a `SYSTEM` user for internal audit.
- Non-goals: no Spring Security, no RBAC tables, no menu permissions, no multi-role accounts, no multi-store merchant isolation, no delivery dispatch model, no administrator employee-management UI/API, and no payment/stock/order-state semantic changes beyond authorization.
