## Context

The current authorization model has three roles: `USER`, `ADMIN`, and `SYSTEM`. `ADMIN` is intentionally documented as a first-version maximum-permission demo operator and currently performs merchant, delivery, platform, and internal mock refund operations. The system now needs clearer business role boundaries while preserving the existing lightweight JWT + `HandlerInterceptor` approach.

This change refines role permissions without introducing Spring Security, RBAC tables, multi-store ownership, multi-role accounts, or a dispatch model. It focuses on making the current demo easier to explain and closer to a real campus ordering flow:

```text
USER pays -> MERCHANT accepts -> DELIVERY delivers/completes -> ADMIN handles platform fallback/refund
```

## Goals / Non-Goals

**Goals:**

- Add explicit `USER`, `MERCHANT`, `DELIVERY`, `ADMIN`, and `SYSTEM` role modeling.
- Keep `SYSTEM` as an internal audit role and continue using `system_timeout` for timeout cancellation stock-release audit.
- Restrict public registration to `USER`.
- Restrict customer flows to `USER` only.
- Split former `ADMIN` operational duties:
  - `MERCHANT` manages categories, dishes, stock, and accepts paid orders.
  - `DELIVERY` starts delivery and completes delivering orders.
  - `ADMIN` remains platform fallback and keeps refund authority.
- Refine order visibility by role.
- Centralize permission checks in `PermissionChecker` and role parsing in a `Role` enum.

**Non-Goals:**

- Do not introduce Spring Security.
- Do not add RBAC tables, menu permissions, permission resources, or role-permission database configuration.
- Do not support multi-role user accounts.
- Do not add shop/store/merchant ownership or multi-tenant isolation.
- Do not add delivery assignment or courier order-claim models.
- Do not add administrator APIs for creating employee accounts.
- Do not allow public registration to create `MERCHANT`, `DELIVERY`, `ADMIN`, or `SYSTEM`.
- Do not change order state-machine, payment, stock, idempotency, timeout, or refund business semantics except for authorization.

## Decisions

1. Add a `Role` enum while keeping `user.role` as a string.

   The schema can remain stable because `user.role` is already `VARCHAR(32)`. Code should use a `Role` enum with values `USER`, `MERCHANT`, `DELIVERY`, `ADMIN`, and `SYSTEM`, plus parsing helpers for JWT and database values.

   Unsupported role values should fail closed with an authentication or permission error. The parser MUST NOT default unknown values to `USER`, `MERCHANT`, `DELIVERY`, `ADMIN`, or `SYSTEM`.

   Alternative considered: create role tables. That would be a different RBAC project and is out of scope.

2. Keep JWT + `HandlerInterceptor`, not Spring Security.

   Login already issues JWTs with a role claim and the interceptor puts user id and role into `BaseContext`. This change extends accepted role values and centralizes parsing/validation. If a role is changed directly in SQL, the user must log in again to receive a token with the new role. This stage will not implement token invalidation for role changes.

3. Public registration creates only `USER`.

   `UserService.register` should create `USER` accounts only. If registration input ever carries a role field, the request should be rejected rather than persisted as `MERCHANT`, `DELIVERY`, `ADMIN`, or `SYSTEM`. `MERCHANT`, `DELIVERY`, and `ADMIN` accounts can be created by SQL/test helpers/local setup documentation in this stage.

4. Keep `SYSTEM` as an internal user-backed audit actor.

   The existing `system_timeout` row remains useful because stock records require an operator id. `SYSTEM` remains invalid for normal login and MUST NOT be treated as an administrator or normal HTTP endpoint role. Permission helpers used for HTTP endpoints should not grant normal access to `SYSTEM`.

   Alternative considered: remove the system user and use `operatorType=SYSTEM` with null operator id. That would require stock-record schema or semantic changes and is unnecessary here.

5. Restrict customer flows to `USER`.

   Cart operations, `POST /order/submit`, `PUT /order/{id}/pay`, pending-payment self-cancel, and own order query/page behavior should require the current role to be `USER`. `ADMIN`, `MERCHANT`, and `DELIVERY` do not act as customers in this stage. `ADMIN` is denied customer mutation flows but keeps administrative order visibility and management actions.

6. Keep `/dish/list` access behavior unchanged.

   Dish browsing is not the risk area for this stage and currently works as a general product listing endpoint. `/dish/list` keeps its existing authentication requirement, if any; this change adds no new role restriction. Category existence checks and cache behavior remain unchanged.

7. Split management and fulfillment permissions.

   Category, dish, and stock management are allowed for `MERCHANT` and `ADMIN`. Order acceptance is allowed for `MERCHANT` and `ADMIN`. Starting delivery and completing delivering orders are allowed for `DELIVERY` and `ADMIN`. Internal mock refund start/complete remains `ADMIN` only.

8. Refine order list/detail visibility by role.

   Lists act as workbenches and stay strict:

   ```text
   USER: own orders in all statuses
   MERCHANT: PAID, ACCEPTED
   DELIVERY: ACCEPTED, DELIVERING
   ADMIN: all statuses
   SYSTEM: no HTTP visibility
   ```

   Details are allowed to show role-relevant history:

   ```text
   USER: own orders in all statuses
   MERCHANT: PAID, ACCEPTED, DELIVERING, COMPLETED, REFUNDING, REFUNDED
   DELIVERY: ACCEPTED, DELIVERING, COMPLETED
   ADMIN: all statuses
   SYSTEM: no HTTP visibility
   ```

   `MERCHANT` does not see `PENDING_PAYMENT` because unpaid orders are not ready for merchant action, and does not see `CANCELLED` in this stage because pending-payment cancellations are customer/system outcomes. This is a first-version global role view, not store isolation or dispatch ownership.

   `MERCHANT` may view refund-related order details for lifecycle awareness, but cannot start or complete mock refunds. Refund operations remain `ADMIN` only.

9. Centralize permissions.

   `PermissionChecker` should expose intent-revealing helpers such as `requireRole`, `requireAnyRole`, `requireUser`, `requireMerchantOrAdmin`, `requireDeliveryOrAdmin`, `requireAdmin`, and `requireOwnerOrAdmin`. Controllers and services should prefer these helpers over direct string comparisons. `requireOwnerOrAdmin` is only for read/admin visibility cases and MUST NOT be used for customer mutation flows such as submit, pay, or self-cancel.

## Risks / Trade-offs

- [Risk] Existing docs/tests assume only `ADMIN` can manage resources. -> Mitigation: update role-specific tests and API docs together with implementation.
- [Risk] Old JWTs carry stale roles after direct SQL updates. -> Mitigation: document that users must log in again after role changes; do not add token invalidation in this stage.
- [Risk] `MERCHANT` and `DELIVERY` global order visibility looks broad. -> Mitigation: explicitly document it as a no-shop/no-dispatch first version; add narrower models later if needed.
- [Risk] Blocking `ADMIN` from customer flows surprises test users. -> Mitigation: document single-role semantics and use separate `USER` accounts for customer testing.
- [Risk] Permission logic spreads during implementation. -> Mitigation: require all role checks to go through `Role` and `PermissionChecker` helpers.
