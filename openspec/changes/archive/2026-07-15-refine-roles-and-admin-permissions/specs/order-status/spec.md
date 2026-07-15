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

#### Scenario: Paid order can enter refunding
- **WHEN** an `ADMIN` starts a refund for a paid order
- **THEN** the order status MUST transition from paid to refunding as an internal mock refund

#### Scenario: Accepted order can enter refunding
- **WHEN** an `ADMIN` starts a refund for an accepted order
- **THEN** the order status MUST transition from accepted to refunding as an internal mock refund

#### Scenario: Refunding order can become refunded
- **WHEN** an `ADMIN` completes refund for a refunding order
- **THEN** the order status MUST transition from refunding to refunded as an internal mock refund completion

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

#### Scenario: User refund request is not supported in this change
- **WHEN** a user attempts to request a refund
- **THEN** the system MUST NOT provide a user refund request transition in this change

### Requirement: Actor Permissions For Order Transitions
The system SHALL restrict order status transition operations by actor role and order ownership.

#### Scenario: USER pays own order
- **WHEN** a logged-in `USER` pays an order they own
- **THEN** the system MUST allow payment initiation only if the order is pending payment

#### Scenario: Non-USER cannot initiate customer payment
- **WHEN** a `MERCHANT`, `DELIVERY`, `ADMIN`, or `SYSTEM` attempts to initiate mock payment through the customer payment endpoint
- **THEN** the system MUST reject the request with a permission error
- **AND** it MUST NOT update payment or order state

#### Scenario: USER cannot pay another user's order
- **WHEN** a logged-in `USER` pays an order owned by another user
- **THEN** the system MUST reject the request as if the order does not exist for that user

#### Scenario: USER cancels own pending order
- **WHEN** a logged-in `USER` cancels an order they own
- **THEN** the system MUST allow the cancellation only if the order is pending payment

#### Scenario: Non-USER cannot use customer cancel
- **WHEN** a `MERCHANT`, `DELIVERY`, `ADMIN`, or `SYSTEM` attempts to cancel an order through the customer cancel endpoint
- **THEN** the system MUST reject the request with a permission error
- **AND** it MUST NOT update order status

#### Scenario: MERCHANT accepts paid order
- **WHEN** a `MERCHANT` accepts a paid order
- **THEN** the system MUST authorize the operation before executing the paid-to-accepted transition

#### Scenario: DELIVERY starts delivery
- **WHEN** a `DELIVERY` starts delivery for an accepted order
- **THEN** the system MUST authorize the operation before executing the accepted-to-delivering transition

#### Scenario: DELIVERY completes delivering order
- **WHEN** a `DELIVERY` completes a delivering order
- **THEN** the system MUST authorize the operation before executing the delivering-to-completed transition

#### Scenario: ADMIN performs platform fallback transitions
- **WHEN** an `ADMIN` accepts an order, starts delivery, completes an order, starts a refund, or completes a refund
- **THEN** the system MUST authorize the operation before executing the status transition as a platform fallback operator

#### Scenario: Refund transitions are ADMIN only
- **WHEN** a `USER`, `MERCHANT`, `DELIVERY`, or `SYSTEM` attempts to start or complete an internal mock refund
- **THEN** the system MUST reject the request with a permission error
- **AND** it MUST NOT update order status

#### Scenario: Unauthorized role cannot perform fulfillment transitions
- **WHEN** a role other than the allowed role for accept, start delivery, complete, refund start, or refund complete attempts that transition
- **THEN** the system MUST reject the request with a permission error
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
