## Requirements

### Requirement: Expanded Order Statuses
The system SHALL support explicit order statuses for pending payment, paid, accepted, delivering, completed, cancelled, refunding, and refunded orders.

#### Scenario: Order status code mapping
- **WHEN** the system stores or returns an order status
- **THEN** it MUST use `1` for pending payment, `2` for paid, `3` for completed, `4` for cancelled, `5` for accepted, `6` for delivering, `7` for refunding, and `8` for refunded

#### Scenario: Numeric status order is not lifecycle order
- **WHEN** the system evaluates lifecycle progress or legal status transitions
- **THEN** it MUST use `OrderStatus` transition rules and MUST NOT infer lifecycle order from numeric status comparisons

#### Scenario: New order starts pending payment
- **WHEN** a user submits an order successfully
- **THEN** the order status MUST be pending payment

### Requirement: Legal Order State Transitions
The system SHALL allow only state-machine-approved order status transitions.

#### Scenario: Pending order can be paid
- **WHEN** a user pays their own pending-payment order
- **THEN** the order status MUST transition from pending payment to paid

#### Scenario: Pending order can be cancelled
- **WHEN** a user cancels their own pending-payment order
- **THEN** the order status MUST transition from pending payment to cancelled

#### Scenario: Paid order can be accepted
- **WHEN** an administrator accepts a paid order
- **THEN** the order status MUST transition from paid to accepted

#### Scenario: Accepted order can start delivery
- **WHEN** an administrator starts delivery for an accepted order
- **THEN** the order status MUST transition from accepted to delivering

#### Scenario: Delivering order can be completed
- **WHEN** an administrator completes a delivering order
- **THEN** the order status MUST transition from delivering to completed

#### Scenario: Paid order can enter refunding
- **WHEN** an administrator starts a refund for a paid order
- **THEN** the order status MUST transition from paid to refunding as an internal mock refund

#### Scenario: Accepted order can enter refunding
- **WHEN** an administrator starts a refund for an accepted order
- **THEN** the order status MUST transition from accepted to refunding as an internal mock refund

#### Scenario: Refunding order can become refunded
- **WHEN** an administrator completes refund for a refunding order
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
- **WHEN** an administrator accepts an already accepted order
- **THEN** the system MUST reject the request and MUST NOT update the order status

#### Scenario: Paid order cannot be completed directly
- **WHEN** an administrator completes a paid order before acceptance and delivery
- **THEN** the system MUST reject the request and MUST NOT update the order status

#### Scenario: Completed order cannot be refunded in first version
- **WHEN** an administrator starts a refund for a completed order
- **THEN** the system MUST reject the request and MUST NOT update the order status

#### Scenario: Delivering order cannot be refunded in first version
- **WHEN** an administrator starts a refund for a delivering order
- **THEN** the system MUST reject the request and MUST NOT update the order status

#### Scenario: User refund request is not supported in this change
- **WHEN** a user attempts to request a refund
- **THEN** the system MUST NOT provide a user refund request transition in this change

### Requirement: Actor Permissions For Order Transitions
The system SHALL restrict order status transition operations by actor role and order ownership.

#### Scenario: User pays own order
- **WHEN** a logged-in user pays an order they own
- **THEN** the system MUST allow the payment only if the order is pending payment

#### Scenario: User cannot pay another user's order
- **WHEN** a logged-in user pays an order owned by another user
- **THEN** the system MUST reject the request as if the order does not exist for that user

#### Scenario: User cancels own pending order
- **WHEN** a logged-in user cancels an order they own
- **THEN** the system MUST allow the cancellation only if the order is pending payment

#### Scenario: Administrator performs super-operator transitions
- **WHEN** an administrator accepts an order, starts delivery, completes an order, starts a refund, or completes a refund
- **THEN** the system MUST authorize the operation before executing the status transition as the first-version maximum-permission demo operator

#### Scenario: Non-admin cannot perform administrator transitions
- **WHEN** a non-admin user attempts to accept an order, start delivery, complete an order, start a refund, or complete a refund
- **THEN** the system MUST reject the request with a permission error and MUST NOT update the order status

### Requirement: Order Status Persistence Uses Conditional Updates
The system SHALL persist every order status transition with a database condition that requires the expected previous status.

#### Scenario: Conditional update succeeds for expected status
- **WHEN** the order row still has the expected previous status during a transition
- **THEN** the system MUST update the row to the new status

#### Scenario: Conditional update fails for changed status
- **WHEN** the order row no longer has the expected previous status during a transition
- **THEN** the system MUST reject the transition and MUST NOT report success
