## MODIFIED Requirements

### Requirement: Mock Payment Creates Payment Record
The system SHALL create or reuse a payment-in-progress record when a pending order starts mock payment, and SHALL finalize payment result through callback handling.

#### Scenario: Pending order starts mock payment
- **WHEN** the current user starts payment for their own pending order through `PUT /order/{id}/pay`
- **THEN** the system MUST persist a `MOCK` payment record with `PAYING` status when no reusable `PAYING` record exists
- **AND** it MUST NOT mark the order paid
- **AND** it MUST NOT confirm locked stock

#### Scenario: Pending order reuses existing paying payment
- **WHEN** the current user starts payment again for their own pending order and a `MOCK` payment record is already `PAYING`
- **THEN** the system MUST return the existing trade number
- **AND** it MUST NOT create another `PAYING` payment record for that retry
- **AND** it MUST NOT mark the order paid

#### Scenario: Concurrent payment initiation creates at most one paying payment
- **WHEN** multiple payment initiation requests concurrently start mock payment for the same pending-payment order
- **THEN** the system MUST prevent multiple `PAYING` payment records from being created for that order
- **AND** successful initiation responses MUST reuse the same current `PAYING` trade number after the first record is created

#### Scenario: Pending order can restart after failed payment
- **WHEN** the current user starts payment for their own pending order after the latest mock payment record failed
- **THEN** the system MUST create a new `PAYING` payment record with a new trade number
- **AND** it MUST leave the order pending payment

#### Scenario: Non-pending order payment rejected
- **WHEN** the current user tries to start payment for an order that is not pending payment
- **THEN** the system MUST reject the payment initiation
- **AND** it MUST NOT create a new `PAYING` payment record

### Requirement: Order Status Remains Source Of Business State
The system SHALL keep the `orders` table as the source of the order business status while using payment records for payment process history.

#### Scenario: Payment initiation does not update order status
- **WHEN** mock payment is initiated through `PUT /order/{id}/pay`
- **THEN** the order status MUST remain pending payment
- **AND** the payment record status MUST be paying

#### Scenario: Successful mock callback updates order and payment record
- **WHEN** a valid successful mock payment callback is processed for a pending-payment order
- **THEN** the order status MUST become paid and the payment record status MUST become successful

#### Scenario: Order status update fails
- **WHEN** the conditional order status update affects zero rows during successful callback processing
- **THEN** the system MUST NOT leave a successful payment record for that failed payment attempt

### Requirement: Lock-Rejected Payment Does Not Create Successful Record
The system SHALL NOT leave a successful payment record when a payment request is rejected before entering the payment success flow because the order status lock cannot be acquired.

#### Scenario: Callback lock acquisition fails
- **WHEN** a successful callback cannot acquire `lock:order:status:{orderId}`
- **THEN** the system MUST reject or retry the payment success processing
- **AND** it MUST NOT create or leave a successful payment record for that callback
- **AND** it MUST NOT update the order to paid

## ADDED Requirements

### Requirement: Payment Trade Number Is Unique
The system SHALL keep payment record trade numbers unique so callbacks can unambiguously identify one payment record.

#### Scenario: Callback lookup by trade number is unambiguous
- **WHEN** a callback is matched by trade number
- **THEN** at most one payment record MUST exist for that trade number

### Requirement: Payment Initiation Response
The system SHALL return mock payment initiation data when `PUT /order/{id}/pay` succeeds.

#### Scenario: Payment initiation returns trade data
- **WHEN** the current user starts or reuses mock payment for a pending order
- **THEN** the response MUST include order ID, order number, current order status, amount, trade number, payment status `PAYING`, and request time

### Requirement: Payment Callback Updates Payment Record
The system SHALL update only paying payment records according to valid mock callback results.

#### Scenario: Successful callback saves success fields
- **WHEN** a valid successful callback is processed for a `PAYING` payment record
- **THEN** the payment record MUST store success time, callback time, optional third-party trade number, and successful status

#### Scenario: Successful callback stops when payment update affects zero rows
- **WHEN** a successful callback attempts to update a payment record from paying to successful and the conditional update affects zero rows
- **THEN** the system MUST stop before updating the order to paid
- **AND** it MUST NOT confirm locked stock
- **AND** it MUST record the callback as duplicate or ignored according to the current payment status

#### Scenario: Failed callback saves failure fields
- **WHEN** a valid failed callback is processed for a `PAYING` payment record
- **THEN** the payment record MUST store callback time, optional third-party trade number, failure reason when available, and failed status

#### Scenario: Failed callback for terminal payment is no-op
- **WHEN** a failed callback arrives for a payment record whose status is successful, failed, or closed
- **THEN** the system MUST record the callback as a business no-op
- **AND** it MUST NOT change the payment record status
- **AND** it MUST NOT update order status or stock

#### Scenario: Successful payment record is terminal
- **WHEN** a later callback arrives for a successful payment record
- **THEN** the system MUST NOT change that payment record to failed or closed
- **AND** it MUST NOT repeat order or stock side effects

#### Scenario: Failed payment record is terminal for the same trade
- **WHEN** a later callback arrives for a failed payment record with the same trade number
- **THEN** the system MUST NOT change that payment record to successful or closed
- **AND** it MUST NOT update order status or stock for that trade

#### Scenario: Closed payment record is terminal
- **WHEN** a later callback arrives for a closed payment record
- **THEN** the system MUST NOT reopen or mark successful that payment record
- **AND** it MUST NOT update order status or stock for that trade

### Requirement: Cancellation Closes In-Progress Payment
The system SHALL close current in-progress mock payment records when a pending-payment order is successfully cancelled.

#### Scenario: Manual cancellation closes paying payment record
- **WHEN** a user cancellation successfully transitions an order from pending payment to cancelled
- **THEN** the system MUST conditionally close any current `MOCK` `PAYING` payment record for that order in the same transaction

#### Scenario: Timeout cancellation closes paying payment record
- **WHEN** timeout cancellation successfully transitions an order from pending payment to cancelled
- **THEN** the system MUST conditionally close any current `MOCK` `PAYING` payment record for that order in the same transaction

#### Scenario: Cancellation close affects only paying mock payments
- **WHEN** cancellation closes in-progress payment records
- **THEN** the close update MUST affect only records for that order whose payment channel is `MOCK` and whose status is `PAYING`

#### Scenario: Cancellation no-op does not close unrelated payments
- **WHEN** cancellation does not transition the order from pending payment to cancelled
- **THEN** the system MUST NOT close payment records for unrelated orders
