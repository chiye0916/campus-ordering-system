## Purpose

Payment records keep an auditable payment trail for orders while the `orders` table remains the source of order business status.

## Requirements

### Requirement: Payment Record Table
The system SHALL store payment attempt/result data in a dedicated `payment_record` table separate from the `orders` table.

#### Scenario: Payment table stores order payment identity
- **WHEN** a payment record is created for an order
- **THEN** the record MUST include order ID, order number, user ID, amount, payment channel, internal trade number, payment status, request time, create time, and update time

#### Scenario: Payment table supports future provider callbacks
- **WHEN** the system stores a payment record
- **THEN** the record MUST have nullable fields for third-party trade number, callback time, and failure reason

### Requirement: Mock Payment Creates Payment Record
The system SHALL create a payment record when a pending order is paid through the mock payment endpoint.

#### Scenario: Pending order paid successfully
- **WHEN** the current user pays their own pending order through `PUT /order/{id}/pay`
- **THEN** the system MUST persist a `MOCK` payment record for that order and mark it successful

#### Scenario: Non-pending order payment rejected
- **WHEN** the current user tries to pay an order that is not pending payment
- **THEN** the system MUST reject the payment and MUST NOT create a successful payment record

### Requirement: Order Status Remains Source Of Business State
The system SHALL keep the `orders` table as the source of the order business status while using payment records for payment process history.

#### Scenario: Successful mock payment updates order and payment record
- **WHEN** a mock payment succeeds
- **THEN** the order status MUST become paid and the payment record status MUST become successful

#### Scenario: Order status update fails
- **WHEN** the conditional order status update affects zero rows
- **THEN** the system MUST NOT leave a successful payment record for that failed payment attempt

### Requirement: Lock-Rejected Payment Does Not Create Successful Record
The system SHALL NOT leave a successful payment record when a payment request is rejected before entering the payment flow because the order status lock cannot be acquired.

#### Scenario: Payment lock acquisition fails
- **WHEN** a payment request cannot acquire `lock:order:status:{orderId}`
- **THEN** the system MUST reject the payment and MUST NOT create a successful payment record
