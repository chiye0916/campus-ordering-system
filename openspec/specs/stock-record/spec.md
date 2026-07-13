## Purpose

Stock records provide an append-only audit trail for stock-changing operations.

## Requirements

### Requirement: Stock Record Table
The system SHALL store stock-changing operations in an append-only stock record table.

#### Scenario: Stock record stores stock before and after values
- **WHEN** the system writes a stock record
- **THEN** the record MUST include dish ID, optional order ID, change type, change quantity, available stock before and after, locked stock before and after, optional operator ID, optional remark, and create time

#### Scenario: Stock record before and after values are row-lock based
- **WHEN** the system writes a stock record
- **THEN** the before and after values MUST be based on a stock row read with a database row lock in the same transaction

### Requirement: Administrator Stock Set Creates Record
The system SHALL write a stock record when an administrator sets dish stock.

#### Scenario: Set stock record
- **WHEN** an administrator sets available stock for a dish
- **THEN** the system MUST write a `SET` stock record with before and after stock values

#### Scenario: Set change quantity is available delta
- **WHEN** the system writes a `SET` stock record
- **THEN** `change_quantity` MUST equal `available_after - available_before`

#### Scenario: Set operator is administrator
- **WHEN** the system writes a `SET` stock record
- **THEN** `operator_id` MUST be the current administrator user ID

### Requirement: Order Submit Lock Creates Record
The system SHALL write stock records when order submission locks stock.

#### Scenario: Lock stock record
- **WHEN** order submission locks stock for a dish
- **THEN** the system MUST write a `LOCK` stock record linked to the created order

#### Scenario: Lock record has order and submit operator
- **WHEN** the system writes a `LOCK` stock record
- **THEN** `order_id` MUST be the created order ID
- **AND** `operator_id` MUST be the submitting user ID

#### Scenario: Failed submit writes no lock record
- **WHEN** order submission fails and the transaction rolls back
- **THEN** the system MUST NOT leave a committed `LOCK` stock record for that failed submission

### Requirement: Payment Confirmation Creates Record
The system SHALL write stock records when payment confirms locked stock consumption.

#### Scenario: Confirm stock record
- **WHEN** payment confirms locked stock consumption for a dish
- **THEN** the system MUST write a `CONFIRM` stock record linked to the paid order

#### Scenario: Confirm record has order and pay operator
- **WHEN** the system writes a `CONFIRM` stock record
- **THEN** `order_id` MUST be the paid order ID
- **AND** `operator_id` MUST be the current pay operator ID when available

#### Scenario: Failed payment writes no confirm record
- **WHEN** payment fails and the transaction rolls back
- **THEN** the system MUST NOT leave a committed `CONFIRM` stock record for that failed payment

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

### Requirement: Cancellation Release Creates Record
The system SHALL write stock records when pending-payment cancellation releases locked stock.

#### Scenario: Release stock record
- **WHEN** cancellation releases locked stock for a dish
- **THEN** the system MUST write a `RELEASE` stock record linked to the cancelled order

#### Scenario: Release record has order and cancel operator
- **WHEN** the system writes a `RELEASE` stock record
- **THEN** `order_id` MUST be the cancelled order ID
- **AND** `operator_id` MUST be the current cancel operator ID when available

#### Scenario: Failed cancellation writes no release record
- **WHEN** cancellation fails and the transaction rolls back
- **THEN** the system MUST NOT leave a committed `RELEASE` stock record for that failed cancellation

### Requirement: Timeout Cancellation Release Uses System Operator
The system SHALL write timeout cancellation stock release records with the system timeout audit user as operator.

#### Scenario: Timeout release record has system operator
- **WHEN** timeout cancellation releases locked stock for an order
- **THEN** the system MUST write a `RELEASE` stock record linked to the cancelled order
- **AND** `operator_id` MUST be the user ID of `system_timeout`

#### Scenario: Timeout release record has timeout remark
- **WHEN** timeout cancellation writes a `RELEASE` stock record
- **THEN** the record remark MUST identify that the release came from automatic order timeout cancellation

#### Scenario: Timeout cancellation no-op writes no release record
- **WHEN** timeout cancellation processes an order that is not pending payment
- **THEN** the system MUST NOT write a `RELEASE` stock record
