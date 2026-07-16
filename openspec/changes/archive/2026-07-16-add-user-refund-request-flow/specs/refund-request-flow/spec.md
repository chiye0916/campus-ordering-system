## ADDED Requirements

### Requirement: Refund Request Records
The system SHALL persist each user refund request as a refund request business record.

#### Scenario: Refund request stores business snapshot
- **WHEN** a refund request is created successfully
- **THEN** the system MUST store the order id, user id, order number, refund amount, reason, request status, and creation time
- **AND** the refund amount MUST match the order amount at request creation time
- **AND** the refund amount MUST be generated from the order and MUST NOT be accepted from the client request

#### Scenario: Refund request has refund number
- **WHEN** a refund request is created successfully
- **THEN** the system MUST generate and persist a unique `refund_no`
- **AND** it MUST NOT accept `refund_no` from the client request

#### Scenario: One order has at most one refund request
- **WHEN** a refund request already exists for an order
- **THEN** the system MUST reject any later refund request for the same order
- **AND** it MUST reject the later request even if the existing request is `REJECTED` or `COMPLETED`

#### Scenario: Rejected request is terminal in first version
- **WHEN** a refund request for an order has status `REJECTED`
- **THEN** the same order MUST NOT be allowed to create another refund request in this version

### Requirement: User Creates Refund Request
The system SHALL allow a logged-in `USER` to request a refund for their own eligible order.

#### Scenario: USER requests refund for paid order
- **WHEN** a logged-in `USER` requests a refund for their own `PAID` order with a non-blank reason
- **THEN** the system MUST create a refund request with status `PENDING_REVIEW`
- **AND** it MUST NOT change the order status
- **AND** it MUST NOT change dish stock

#### Scenario: USER requests refund for accepted order
- **WHEN** a logged-in `USER` requests a refund for their own `ACCEPTED` order with a non-blank reason
- **THEN** the system MUST create a refund request with status `PENDING_REVIEW`
- **AND** it MUST NOT change the order status
- **AND** it MUST NOT change dish stock

#### Scenario: USER cannot request refund for another user's order
- **WHEN** a logged-in `USER` requests a refund for an order owned by another user
- **THEN** the system MUST reject the request as if the order does not exist for that user
- **AND** it MUST NOT create a refund request

#### Scenario: Non-USER cannot create refund request
- **WHEN** a `MERCHANT`, `DELIVERY`, `ADMIN`, or `SYSTEM` attempts to create a user refund request
- **THEN** the system MUST reject the request with a permission error
- **AND** it MUST NOT create a refund request

#### Scenario: Ineligible order status cannot request refund
- **WHEN** a `USER` requests a refund for an order whose status is not `PAID` or `ACCEPTED`
- **THEN** the system MUST reject the request
- **AND** it MUST NOT create a refund request

### Requirement: Refund Request Visibility
The system SHALL scope refund request list and detail visibility by actor role.

#### Scenario: USER lists own refund requests
- **WHEN** a logged-in `USER` lists refund requests through the user refund request list endpoint
- **THEN** the system MUST return only refund requests owned by that user
- **AND** it MUST support `page`, `pageSize`, and optional `status` query parameters

#### Scenario: ADMIN lists refund requests
- **WHEN** a logged-in `ADMIN` lists refund requests through the administrator refund request list endpoint
- **THEN** the system MUST allow visibility of all refund requests
- **AND** it MUST support `page`, `pageSize`, and optional `status` query parameters

#### Scenario: USER reads own refund request detail
- **WHEN** a logged-in `USER` reads a refund request detail for a request they own
- **THEN** the system MUST return the refund request detail

#### Scenario: USER cannot read another user's refund request detail
- **WHEN** a logged-in `USER` reads a refund request detail owned by another user
- **THEN** the system MUST reject the request as if the refund request does not exist for that user

#### Scenario: ADMIN reads refund request detail
- **WHEN** a logged-in `ADMIN` reads any refund request detail
- **THEN** the system MUST return the refund request detail

#### Scenario: Other roles cannot use refund request visibility
- **WHEN** a `MERCHANT`, `DELIVERY`, or `SYSTEM` attempts to list or read refund requests
- **THEN** the system MUST reject the request with a permission error

### Requirement: Admin Approves Refund Request
The system SHALL allow an `ADMIN` to approve a pending refund request and move the related order into refunding.

#### Scenario: ADMIN approves pending request
- **WHEN** a logged-in `ADMIN` approves a `PENDING_REVIEW` refund request whose order is still `PAID` or `ACCEPTED`
- **THEN** the system MUST update the refund request status to `APPROVED`
- **AND** it MUST set the reviewer id and review time
- **AND** it MUST update the order status to `REFUNDING`
- **AND** all updates MUST happen in the same transaction
- **AND** both conditional updates MUST affect one row

#### Scenario: Approval rejects non-pending request
- **WHEN** an `ADMIN` approves a refund request whose status is not `PENDING_REVIEW`
- **THEN** the system MUST reject the approval
- **AND** it MUST NOT update the order status

#### Scenario: Approval rejects changed order status
- **WHEN** an `ADMIN` approves a pending refund request whose order is no longer `PAID` or `ACCEPTED`
- **THEN** the system MUST reject the approval
- **AND** it MUST NOT update the refund request to `APPROVED`

#### Scenario: Non-ADMIN cannot approve refund request
- **WHEN** a `USER`, `MERCHANT`, `DELIVERY`, or `SYSTEM` attempts to approve a refund request
- **THEN** the system MUST reject the request with a permission error
- **AND** it MUST NOT update the refund request or order status

### Requirement: Admin Rejects Refund Request
The system SHALL allow an `ADMIN` to reject a pending refund request without changing the order status.

#### Scenario: ADMIN rejects pending request
- **WHEN** a logged-in `ADMIN` rejects a `PENDING_REVIEW` refund request with a non-blank reject reason
- **THEN** the system MUST update the refund request status to `REJECTED`
- **AND** it MUST store the reject reason
- **AND** it MUST set the reviewer id and review time
- **AND** it MUST update the refund request update time
- **AND** it MUST NOT change the order status

#### Scenario: Reject requires reason
- **WHEN** an `ADMIN` rejects a refund request with a blank reject reason
- **THEN** the system MUST reject the request
- **AND** it MUST NOT update the refund request

#### Scenario: Reject rejects non-pending request
- **WHEN** an `ADMIN` rejects a refund request whose status is not `PENDING_REVIEW`
- **THEN** the system MUST reject the request
- **AND** it MUST NOT update the refund request or order status

#### Scenario: Non-ADMIN cannot reject refund request
- **WHEN** a `USER`, `MERCHANT`, `DELIVERY`, or `SYSTEM` attempts to reject a refund request
- **THEN** the system MUST reject the request with a permission error
- **AND** it MUST NOT update the refund request or order status

### Requirement: Admin Completes Refund Request
The system SHALL allow an `ADMIN` to complete an approved refund request and move the related order into refunded.

#### Scenario: ADMIN completes approved request
- **WHEN** a logged-in `ADMIN` completes an `APPROVED` refund request whose order is `REFUNDING`
- **THEN** the system MUST update the refund request status to `COMPLETED`
- **AND** it MUST set the complete time
- **AND** it MUST update the order status to `REFUNDED`
- **AND** all updates MUST happen in the same transaction
- **AND** both conditional updates MUST affect one row

#### Scenario: Complete rejects non-approved request
- **WHEN** an `ADMIN` completes a refund request whose status is not `APPROVED`
- **THEN** the system MUST reject the request
- **AND** it MUST NOT update the order status

#### Scenario: Complete rejects changed order status
- **WHEN** an `ADMIN` completes an approved refund request whose order is not `REFUNDING`
- **THEN** the system MUST reject the request
- **AND** it MUST NOT update the refund request to `COMPLETED`

#### Scenario: Non-ADMIN cannot complete refund request
- **WHEN** a `USER`, `MERCHANT`, `DELIVERY`, or `SYSTEM` attempts to complete a refund request
- **THEN** the system MUST reject the request with a permission error
- **AND** it MUST NOT update the refund request or order status

### Requirement: Refund Request Does Not Restore Stock
The system SHALL NOT restore dish stock or write stock records during refund request processing.

#### Scenario: Refund request creation has no stock side effect
- **WHEN** a user creates a refund request
- **THEN** the system MUST NOT change `dish_stock`
- **AND** it MUST NOT write `stock_record`

#### Scenario: Refund approval has no stock side effect
- **WHEN** an administrator approves a refund request
- **THEN** the system MUST NOT change `dish_stock`
- **AND** it MUST NOT write `stock_record`

#### Scenario: Refund completion has no stock side effect
- **WHEN** an administrator completes a refund request
- **THEN** the system MUST NOT change `dish_stock`
- **AND** it MUST NOT write `stock_record`

### Requirement: Legacy Order Refund APIs Remain Fallback
The system SHALL keep existing order-level mock refund APIs available as administrator internal fallback operations.

#### Scenario: Legacy start refund remains available
- **WHEN** an `ADMIN` calls `PUT /order/{id}/refund/start` for an eligible order with no refund request
- **THEN** the system MUST process the existing internal mock refund start behavior
- **AND** it MUST NOT require a `refund_request`

#### Scenario: Legacy complete refund remains available
- **WHEN** an `ADMIN` calls `PUT /order/{id}/refund/complete` for an eligible order with no refund request
- **THEN** the system MUST process the existing internal mock refund completion behavior
- **AND** it MUST NOT require a `refund_request`

#### Scenario: Legacy refund start rejects existing refund request
- **WHEN** an `ADMIN` calls `PUT /order/{id}/refund/start` for an order that already has a refund request
- **THEN** the system MUST reject the fallback operation
- **AND** it MUST NOT update the order status
- **AND** it MUST indicate that the administrator should use the `/refund/...` workflow

#### Scenario: Legacy refund complete rejects existing refund request
- **WHEN** an `ADMIN` calls `PUT /order/{id}/refund/complete` for an order that already has a refund request
- **THEN** the system MUST reject the fallback operation
- **AND** it MUST NOT update the order status
- **AND** it MUST indicate that the administrator should use the `/refund/...` workflow

#### Scenario: Recommended business flow uses refund request APIs
- **WHEN** documentation describes the normal user refund workflow
- **THEN** it MUST describe `/refund/...` refund request APIs as the recommended business flow
- **AND** it MUST identify order-level refund APIs as internal mock fallback APIs
