## ADDED Requirements

### Requirement: Payment Callback Confirmation Creates Record
The system SHALL write stock records when successful mock payment callback processing confirms locked stock consumption.

#### Scenario: Callback confirm stock record
- **WHEN** successful callback processing confirms locked stock consumption for a dish
- **THEN** the system MUST write a `CONFIRM` stock record linked to the paid order

#### Scenario: Callback confirm record has order and operator
- **WHEN** the system writes a `CONFIRM` stock record during callback processing
- **THEN** `order_id` MUST be the paid order ID
- **AND** `operator_id` MUST be the order user ID or the available payment operator ID

#### Scenario: Failed callback writes no confirm record
- **WHEN** callback processing fails before the order is paid
- **THEN** the system MUST NOT leave a committed `CONFIRM` stock record for that callback

### Requirement: Duplicate Or Late Callback Writes No Extra Stock Record
The system SHALL not write duplicate stock records for callback requests that do not produce a first successful paid transition.

#### Scenario: Duplicate callback writes no duplicate confirm
- **WHEN** a duplicate successful callback is received for an already paid order
- **THEN** the system MUST NOT write another `CONFIRM` stock record

#### Scenario: Late callback after cancellation writes no confirm
- **WHEN** a successful callback is received after the order has been cancelled
- **THEN** the system MUST NOT write a `CONFIRM` stock record

#### Scenario: Failed callback writes no release record
- **WHEN** a failed callback is processed
- **THEN** the system MUST NOT write a `RELEASE` stock record
