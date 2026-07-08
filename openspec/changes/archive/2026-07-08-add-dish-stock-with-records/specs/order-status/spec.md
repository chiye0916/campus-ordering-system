## ADDED Requirements

### Requirement: Payment Confirms Stock Consumption
The system SHALL confirm locked stock consumption as part of successful payment.

#### Scenario: Paid transition confirms stock
- **WHEN** a pending-payment order transitions to paid
- **THEN** the system MUST confirm locked stock consumption for each order detail in the same transaction

#### Scenario: Payment confirms stock only after status update succeeds
- **WHEN** the conditional update from pending payment to paid affects one row
- **THEN** the system MUST confirm locked stock consumption and write `CONFIRM` stock records

#### Scenario: Duplicate payment does not confirm stock twice
- **WHEN** the conditional update from pending payment to paid affects zero rows
- **THEN** the system MUST NOT confirm locked stock consumption
- **AND** the system MUST NOT write `CONFIRM` stock records

#### Scenario: Payment stock confirmation failure prevents paid status
- **WHEN** stock confirmation fails during payment
- **THEN** the order MUST NOT transition to paid

### Requirement: Pending Cancellation Releases Stock
The system SHALL release locked stock as part of pending-payment order cancellation.

#### Scenario: Pending cancellation releases stock
- **WHEN** a pending-payment order transitions to cancelled
- **THEN** the system MUST release locked stock for each order detail in the same transaction

#### Scenario: Cancellation releases stock only after status update succeeds
- **WHEN** the conditional update from pending payment to cancelled affects one row
- **THEN** the system MUST release locked stock and write `RELEASE` stock records

#### Scenario: Duplicate cancellation does not release stock twice
- **WHEN** the conditional update from pending payment to cancelled affects zero rows
- **THEN** the system MUST NOT release locked stock
- **AND** the system MUST NOT write `RELEASE` stock records

#### Scenario: Cancellation stock release failure prevents cancelled status
- **WHEN** stock release fails during cancellation
- **THEN** the order MUST NOT transition to cancelled

### Requirement: Non-Stock Status Transitions Do Not Change Stock
The system SHALL not change stock during order status transitions that do not represent submit, payment confirmation, or pending-payment cancellation.

#### Scenario: Accept does not change stock
- **WHEN** an administrator accepts an order
- **THEN** the system MUST NOT change dish stock

#### Scenario: Delivery and completion do not change stock
- **WHEN** an administrator starts delivery or completes an order
- **THEN** the system MUST NOT change dish stock

#### Scenario: Mock refund transitions do not change stock
- **WHEN** an administrator starts or completes an internal mock refund
- **THEN** the system MUST NOT change dish stock
