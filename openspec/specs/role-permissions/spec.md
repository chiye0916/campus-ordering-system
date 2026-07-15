## Purpose

Role permissions define supported roles, public registration limits, JWT/BaseContext role handling, centralized permission helpers, API role boundaries, and order visibility rules.

## Requirements

### Requirement: Supported Roles Are Explicit
The system SHALL model roles explicitly as `USER`, `MERCHANT`, `DELIVERY`, `ADMIN`, and `SYSTEM`.

#### Scenario: Role enum contains supported roles
- **WHEN** application code evaluates user roles
- **THEN** it MUST use a role model that supports `USER`, `MERCHANT`, `DELIVERY`, `ADMIN`, and `SYSTEM`

#### Scenario: Unknown role is rejected
- **WHEN** a JWT or database user record contains an unsupported role value
- **THEN** the system MUST fail closed with an authentication or permission error
- **AND** it MUST NOT treat the role as `USER`, `MERCHANT`, `DELIVERY`, `ADMIN`, or `SYSTEM`
- **AND** it MUST NOT default the role to any privileged or customer role

### Requirement: Public Registration Creates Users Only
The system SHALL prevent public registration from creating privileged or internal roles.

#### Scenario: Public registration creates USER
- **WHEN** a user registers through the public registration API
- **THEN** the created account role MUST be `USER`

#### Scenario: Public registration cannot create privileged roles
- **WHEN** a public registration request attempts to create `MERCHANT`, `DELIVERY`, `ADMIN`, or `SYSTEM`
- **THEN** the system MUST reject the role input
- **AND** it MUST NOT create the account with that requested role

#### Scenario: Public registration persists USER only
- **WHEN** a public registration request is accepted
- **THEN** the persisted account role MUST be `USER`

### Requirement: SYSTEM Is Internal Only
The system SHALL keep `SYSTEM` as an internal audit role, not a normal login role.

#### Scenario: SYSTEM user cannot login normally
- **WHEN** a `SYSTEM` user attempts to authenticate through the normal login API
- **THEN** the system MUST reject the login

#### Scenario: system_timeout remains audit subject
- **WHEN** the timeout cancellation flow releases locked stock
- **THEN** it MUST continue to use the `system_timeout` user as the audit operator
- **AND** the `system_timeout` user role MUST be `SYSTEM`

#### Scenario: SYSTEM is not administrator
- **WHEN** normal HTTP authorization checks evaluate a `SYSTEM` role
- **THEN** the system MUST NOT treat it as `ADMIN`, `MERCHANT`, `DELIVERY`, or `USER`

#### Scenario: SYSTEM is not normal endpoint role
- **WHEN** permission helpers authorize normal HTTP endpoint access
- **THEN** they MUST NOT grant endpoint access to `SYSTEM`

### Requirement: JWT Role Context Supports New Roles
The system SHALL carry the current user's role through JWT parsing and request context.

#### Scenario: Login token includes role
- **WHEN** a `USER`, `MERCHANT`, `DELIVERY`, or `ADMIN` logs in successfully
- **THEN** the issued JWT MUST include that role
- **AND** the login response MUST expose that role consistently with existing response shape

#### Scenario: Interceptor stores current role
- **WHEN** an authenticated request is accepted
- **THEN** the interceptor MUST parse the role from JWT
- **AND** it MUST store the current role in request context for permission checks

#### Scenario: Role change requires relogin
- **WHEN** a user's role is changed outside the current token
- **THEN** the user MUST log in again before the new role appears in JWT-based authorization
- **AND** this stage MUST NOT require forced token invalidation for role changes

### Requirement: Permission Checks Are Centralized
The system SHALL centralize role and ownership authorization in permission helper code.

#### Scenario: Role checks use helper methods
- **WHEN** controllers or services authorize role-specific behavior
- **THEN** they MUST use centralized helpers such as `requireRole`, `requireAnyRole`, `requireUser`, `requireMerchantOrAdmin`, `requireDeliveryOrAdmin`, or `requireAdmin`

#### Scenario: Owner-or-admin checks are centralized
- **WHEN** an operation needs to allow either the resource owner or an administrator
- **THEN** it MUST use a centralized owner-or-admin helper or equivalent service-level helper instead of duplicated role string checks

#### Scenario: Owner-or-admin is not used for customer mutations
- **WHEN** a customer mutation flow such as cart mutation, order submit, payment initiation, or pending-payment self-cancel is authorized
- **THEN** the system MUST require `USER`
- **AND** it MUST NOT use owner-or-admin authorization to allow `ADMIN` to perform that customer mutation

### Requirement: Customer Flows Require USER
The system SHALL restrict customer shopping and own-order flows to `USER`.

#### Scenario: USER can use customer cart and order flow
- **WHEN** a logged-in `USER` uses cart operations, submits an order, initiates mock payment, cancels their own pending-payment order, or views their own orders
- **THEN** the system MUST allow the operation subject to existing ownership and state rules

#### Scenario: Non-USER cannot use customer cart and order flow
- **WHEN** a `MERCHANT`, `DELIVERY`, `ADMIN`, or `SYSTEM` attempts to use cart operations, submit an order, initiate mock payment, cancel an own order as a customer, or view orders through the customer-only scope
- **THEN** the system MUST reject the request with a permission error

### Requirement: Dish List Access Is Unchanged
The system SHALL keep `/dish/list` access behavior unchanged by this role refinement.

#### Scenario: Dish list does not add role restriction
- **WHEN** `/dish/list` is requested under the existing authentication and validation behavior
- **THEN** the system MUST NOT add a new `USER`-only, `MERCHANT`-only, `DELIVERY`-only, or `ADMIN`-only role restriction in this stage
- **AND** existing category validation and cache behavior MUST remain unchanged

#### Scenario: Dish list keeps existing authentication behavior
- **WHEN** this change is implemented
- **THEN** `/dish/list` MUST keep its existing authentication requirement, if any
- **AND** this change MUST NOT make the endpoint newly public or newly role-restricted

### Requirement: Merchant Management Permissions
The system SHALL allow `MERCHANT` and `ADMIN` to perform merchant management operations.

#### Scenario: MERCHANT manages catalog and stock
- **WHEN** a `MERCHANT` creates, updates, lists management pages for, or changes status of categories and dishes, or reads/sets dish stock
- **THEN** the system MUST authorize the operation

#### Scenario: ADMIN manages catalog and stock
- **WHEN** an `ADMIN` creates, updates, lists management pages for, or changes status of categories and dishes, or reads/sets dish stock
- **THEN** the system MUST authorize the operation

#### Scenario: Other roles cannot manage catalog and stock
- **WHEN** a `USER`, `DELIVERY`, or `SYSTEM` attempts category, dish, or stock management
- **THEN** the system MUST reject the request with a permission error

### Requirement: Order List Visibility Follows Workbench Scope
The system SHALL scope order list/page visibility by role as a current-work workbench.

#### Scenario: USER lists own orders only
- **WHEN** a `USER` lists orders
- **THEN** the system MUST return only orders owned by that user
- **AND** it MUST include that user's orders in all statuses

#### Scenario: MERCHANT lists paid and accepted orders
- **WHEN** a `MERCHANT` lists orders
- **THEN** the system MUST return global orders whose statuses are `PAID` or `ACCEPTED`
- **AND** it MUST NOT return `PENDING_PAYMENT`, `DELIVERING`, `COMPLETED`, `CANCELLED`, `REFUNDING`, or `REFUNDED` orders in the merchant list

#### Scenario: DELIVERY lists accepted and delivering orders
- **WHEN** a `DELIVERY` lists orders
- **THEN** the system MUST return global orders whose statuses are `ACCEPTED` or `DELIVERING`
- **AND** it MUST NOT return `PENDING_PAYMENT`, `PAID`, `COMPLETED`, `CANCELLED`, `REFUNDING`, or `REFUNDED` orders in the delivery list

#### Scenario: ADMIN lists all orders
- **WHEN** an `ADMIN` lists orders
- **THEN** the system MUST allow visibility of all orders in all statuses

#### Scenario: SYSTEM cannot list orders
- **WHEN** a `SYSTEM` role reaches normal HTTP order list authorization
- **THEN** the system MUST reject the request with a permission error

### Requirement: Order Detail Visibility Follows Role History Scope
The system SHALL scope order detail visibility by role as role-relevant order history.

#### Scenario: USER reads own order details only
- **WHEN** a `USER` reads order detail
- **THEN** the system MUST allow details only for orders owned by that user
- **AND** it MUST hide other users' orders as not visible to that user

#### Scenario: MERCHANT reads role-relevant order details
- **WHEN** a `MERCHANT` reads order detail
- **THEN** the system MUST allow global order details whose statuses are `PAID`, `ACCEPTED`, `DELIVERING`, `COMPLETED`, `REFUNDING`, or `REFUNDED`
- **AND** it MUST NOT allow `PENDING_PAYMENT` or `CANCELLED` order details for merchant visibility in this stage

#### Scenario: MERCHANT refund detail visibility does not grant refund operation
- **WHEN** a `MERCHANT` can read an order detail in `REFUNDING` or `REFUNDED` status
- **THEN** that visibility MUST NOT authorize the `MERCHANT` to start or complete mock refunds
- **AND** refund start and refund complete operations MUST remain `ADMIN` only

#### Scenario: DELIVERY reads role-relevant order details
- **WHEN** a `DELIVERY` reads order detail
- **THEN** the system MUST allow global order details whose statuses are `ACCEPTED`, `DELIVERING`, or `COMPLETED`
- **AND** it MUST NOT allow `PENDING_PAYMENT`, `PAID`, `CANCELLED`, `REFUNDING`, or `REFUNDED` order details for delivery visibility in this stage

#### Scenario: ADMIN reads all order details
- **WHEN** an `ADMIN` reads order detail
- **THEN** the system MUST allow visibility of all order details in all statuses

#### Scenario: SYSTEM cannot read order details
- **WHEN** a `SYSTEM` role reaches normal HTTP order detail authorization
- **THEN** the system MUST reject the request with a permission error

### Requirement: Non-USER Roles Are Created Outside Public Registration
The system SHALL not add a full employee-management API in this stage.

#### Scenario: Privileged roles are setup-only in this stage
- **WHEN** local testing needs `MERCHANT`, `DELIVERY`, or `ADMIN` users
- **THEN** the project MAY use SQL, seed data, or test helpers to create them
- **AND** this stage MUST NOT require an administrator employee-management API
