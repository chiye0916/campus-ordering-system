## MODIFIED Requirements

### Requirement: Legal Order State Transitions
The system SHALL allow only state-machine-approved order status transitions.

#### Scenario: Pending order can be paid by successful callback
- **WHEN** a valid successful mock payment callback is processed for a pending-payment order
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

### Requirement: Payment Confirms Stock Consumption
The system SHALL confirm locked stock consumption as part of successful mock payment callback processing.

#### Scenario: Successful callback paid transition confirms stock
- **WHEN** a successful mock payment callback transitions a pending-payment order to paid
- **THEN** the system MUST confirm locked stock consumption for each order detail in the same transaction

#### Scenario: Callback confirms stock only after status update succeeds
- **WHEN** the conditional update from pending payment to paid affects one row during successful callback processing
- **THEN** the system MUST confirm locked stock consumption and write `CONFIRM` stock records

#### Scenario: Duplicate callback does not confirm stock twice
- **WHEN** the conditional update from pending payment to paid affects zero rows during callback processing
- **THEN** the system MUST NOT confirm locked stock consumption
- **AND** the system MUST NOT write `CONFIRM` stock records

#### Scenario: Callback stock confirmation failure prevents paid status
- **WHEN** stock confirmation fails during successful callback processing
- **THEN** the order MUST NOT transition to paid

## ADDED Requirements

### Requirement: Payment Initiation Does Not Change Order State
The system SHALL not change order business status when mock payment is only initiated.

#### Scenario: Payment initiation keeps pending status
- **WHEN** a user calls `PUT /order/{id}/pay` for a pending-payment order
- **THEN** the order MUST remain pending payment until a valid successful callback is processed
