## ADDED Requirements

### Requirement: Successful Status Transitions Write History
The system SHALL write order status history for every successful order status transition.

#### Scenario: Order submit writes creation history
- **WHEN** an order is submitted successfully and starts pending payment
- **THEN** the system MUST write an order status history record with operation `ORDER_SUBMIT`
- **AND** the record MUST have `old_status = null` and `new_status = PENDING_PAYMENT`

#### Scenario: Payment success writes paid history
- **WHEN** a valid successful mock payment callback transitions an order from pending payment to paid
- **THEN** the system MUST write an order status history record with operation `PAYMENT_SUCCESS`

#### Scenario: User cancellation writes cancelled history
- **WHEN** a `USER` cancels their own pending-payment order and the order transitions to cancelled
- **THEN** the system MUST write an order status history record with operation `USER_CANCEL`

#### Scenario: Timeout cancellation writes cancelled history
- **WHEN** the system timeout flow transitions a pending-payment order to cancelled
- **THEN** the system MUST write an order status history record with operation `TIMEOUT_CANCEL`

#### Scenario: Merchant accept writes accepted history
- **WHEN** a `MERCHANT` or `ADMIN` accepts a paid order and the order transitions to accepted
- **THEN** the system MUST write an order status history record with operation `MERCHANT_ACCEPT`

#### Scenario: Delivery start writes delivering history
- **WHEN** a `DELIVERY` or `ADMIN` starts delivery for an accepted order and the order transitions to delivering
- **THEN** the system MUST write an order status history record with operation `DELIVERY_START`

#### Scenario: Delivery complete writes completed history
- **WHEN** a `DELIVERY` or `ADMIN` completes a delivering order and the order transitions to completed
- **THEN** the system MUST write an order status history record with operation `DELIVERY_COMPLETE`

#### Scenario: Refund request approval writes refunding history
- **WHEN** an `ADMIN` approves a pending refund request and the order transitions from paid or accepted to refunding
- **THEN** the system MUST write an order status history record with operation `REFUND_REQUEST_APPROVE`

#### Scenario: Refund request completion writes refunded history
- **WHEN** an `ADMIN` completes an approved refund request and the order transitions from refunding to refunded
- **THEN** the system MUST write an order status history record with operation `REFUND_REQUEST_COMPLETE`

#### Scenario: Internal refund start writes refunding history
- **WHEN** an `ADMIN` starts an internal mock refund through the order-level fallback endpoint and the order transitions to refunding
- **THEN** the system MUST write an order status history record with operation `INTERNAL_REFUND_START`

#### Scenario: Internal refund completion writes refunded history
- **WHEN** an `ADMIN` completes an internal mock refund through the order-level fallback endpoint and the order transitions to refunded
- **THEN** the system MUST write an order status history record with operation `INTERNAL_REFUND_COMPLETE`

### Requirement: Failed Or No-Op Transitions Do Not Write History
The system SHALL NOT write order status history when no order status transition occurs.

#### Scenario: Duplicate payment callback does not write duplicate history
- **WHEN** a duplicate successful mock payment callback is processed and the order status does not change
- **THEN** the system MUST NOT write another `PAYMENT_SUCCESS` order status history record

#### Scenario: Timeout cancellation no-op does not write history
- **WHEN** the timeout cancellation flow processes an order that is no longer pending payment
- **THEN** the system MUST NOT write a `TIMEOUT_CANCEL` order status history record

#### Scenario: Conditional update failure does not write history
- **WHEN** a conditional order status update affects zero rows
- **THEN** the system MUST NOT write an order status history record

#### Scenario: Rejected refund request does not write history
- **WHEN** an `ADMIN` rejects a pending refund request and the order status remains unchanged
- **THEN** the system MUST NOT write an order status history record

#### Scenario: Failed payment callback does not write history
- **WHEN** a mock payment failure callback is processed without changing order status
- **THEN** the system MUST NOT write an order status history record
