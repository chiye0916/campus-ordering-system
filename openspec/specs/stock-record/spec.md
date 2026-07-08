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
