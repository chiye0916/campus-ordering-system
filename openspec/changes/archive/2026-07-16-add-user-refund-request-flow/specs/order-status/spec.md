## MODIFIED Requirements

### Requirement: Legal Order State Transitions
The system SHALL allow only state-machine-approved order status transitions.

#### Scenario: Pending order can be paid by successful callback
- **WHEN** a valid successful mock payment callback is processed for a pending-payment order
- **THEN** the order status MUST transition from pending payment to paid

#### Scenario: Pending order can be cancelled
- **WHEN** a `USER` cancels their own pending-payment order
- **THEN** the order status MUST transition from pending payment to cancelled

#### Scenario: Paid order can be accepted
- **WHEN** a `MERCHANT` or `ADMIN` accepts a paid order
- **THEN** the order status MUST transition from paid to accepted

#### Scenario: Accepted order can start delivery
- **WHEN** a `DELIVERY` or `ADMIN` starts delivery for an accepted order
- **THEN** the order status MUST transition from accepted to delivering

#### Scenario: Delivering order can be completed
- **WHEN** a `DELIVERY` or `ADMIN` completes a delivering order
- **THEN** the order status MUST transition from delivering to completed

#### Scenario: Paid order can enter refunding through approved refund request
- **WHEN** an `ADMIN` approves a pending refund request for a paid order
- **THEN** the order status MUST transition from paid to refunding

#### Scenario: Accepted order can enter refunding through approved refund request
- **WHEN** an `ADMIN` approves a pending refund request for an accepted order
- **THEN** the order status MUST transition from accepted to refunding

#### Scenario: Paid order can enter refunding through internal fallback
- **WHEN** an `ADMIN` starts an internal mock refund for a paid order through the order-level fallback endpoint
- **THEN** the order status MUST transition from paid to refunding

#### Scenario: Accepted order can enter refunding through internal fallback
- **WHEN** an `ADMIN` starts an internal mock refund for an accepted order through the order-level fallback endpoint
- **THEN** the order status MUST transition from accepted to refunding

#### Scenario: Refunding order can become refunded through completed refund request
- **WHEN** an `ADMIN` completes an approved refund request for a refunding order
- **THEN** the order status MUST transition from refunding to refunded

#### Scenario: Refunding order can become refunded through internal fallback
- **WHEN** an `ADMIN` completes an internal mock refund for a refunding order through the order-level fallback endpoint
- **THEN** the order status MUST transition from refunding to refunded

### Requirement: Illegal Order State Transitions Are Rejected
The system SHALL reject attempts to perform order status transitions that are not allowed by the state machine.

#### Scenario: Cancelled order cannot be paid
- **WHEN** a user pays a cancelled order
- **THEN** the system MUST reject the payment and MUST NOT update the order status

#### Scenario: Completed order cannot be cancelled
- **WHEN** a user cancels a completed order
- **THEN** the system MUST reject the cancellation and MUST NOT update the order status

#### Scenario: Accepted order cannot be accepted again
- **WHEN** a `MERCHANT` or `ADMIN` accepts an already accepted order
- **THEN** the system MUST reject the request and MUST NOT update the order status

#### Scenario: Paid order cannot be completed directly
- **WHEN** a `DELIVERY` or `ADMIN` completes a paid order before acceptance and delivery
- **THEN** the system MUST reject the request and MUST NOT update the order status

#### Scenario: Completed order cannot be refunded in first version
- **WHEN** an `ADMIN` starts a refund for a completed order
- **THEN** the system MUST reject the request and MUST NOT update the order status

#### Scenario: Delivering order cannot be refunded in first version
- **WHEN** an `ADMIN` starts a refund for a delivering order
- **THEN** the system MUST reject the request and MUST NOT update the order status

#### Scenario: User refund request cannot target ineligible order state
- **WHEN** a user requests a refund for an order whose status is not paid or accepted
- **THEN** the system MUST reject the refund request
- **AND** it MUST NOT update the order status

#### Scenario: Refund request approval cannot refund changed order state
- **WHEN** an `ADMIN` approves a refund request for an order that is no longer paid or accepted
- **THEN** the system MUST reject the approval
- **AND** it MUST NOT update the order status

#### Scenario: Refund request completion cannot complete changed order state
- **WHEN** an `ADMIN` completes a refund request for an order that is no longer refunding
- **THEN** the system MUST reject the completion
- **AND** it MUST NOT update the order status

### Requirement: Non-Stock Status Transitions Do Not Change Stock
The system SHALL not change stock during order status transitions that do not represent submit, payment confirmation, or pending-payment cancellation.

#### Scenario: Accept does not change stock
- **WHEN** a `MERCHANT` or `ADMIN` accepts an order
- **THEN** the system MUST NOT change dish stock

#### Scenario: Delivery and completion do not change stock
- **WHEN** a `DELIVERY` or `ADMIN` starts delivery or completes an order
- **THEN** the system MUST NOT change dish stock

#### Scenario: Mock refund transitions do not change stock
- **WHEN** an `ADMIN` starts or completes an internal mock refund
- **THEN** the system MUST NOT change dish stock

#### Scenario: Refund request transitions do not change stock
- **WHEN** an `ADMIN` approves or completes a refund request
- **THEN** the system MUST NOT change dish stock
