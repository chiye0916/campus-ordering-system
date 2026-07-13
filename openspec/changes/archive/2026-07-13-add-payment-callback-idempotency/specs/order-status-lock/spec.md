## ADDED Requirements

### Requirement: Payment Initiation Uses Concurrency Guard
The system SHALL protect mock payment initiation from concurrently creating multiple `PAYING` payment records for the same order.

#### Scenario: Payment initiation obtains concurrency guard
- **WHEN** a user starts mock payment for a pending-payment order
- **THEN** the system MUST acquire the existing Redis order status lock or an equivalent database row lock before checking for and creating a `PAYING` payment record

#### Scenario: Payment initiation guard failure prevents duplicate creation
- **WHEN** payment initiation cannot acquire its concurrency guard
- **THEN** the system MUST reject the initiation attempt with a retryable busy error
- **AND** it MUST NOT create a new `PAYING` payment record

### Requirement: Payment Callback Uses Order Status Lock
The system SHALL acquire the Redis order status lock before executing successful mock payment callback status transition logic.

#### Scenario: Successful callback obtains order status lock
- **WHEN** a successful mock payment callback attempts to pay an order
- **THEN** the system MUST acquire `lock:order:status:{orderId}` before updating payment success, order status, or confirming stock

#### Scenario: Callback lock acquisition failure has no side effects
- **WHEN** successful callback processing cannot acquire the order status lock
- **THEN** the system MUST NOT update the order to paid
- **AND** it MUST NOT confirm locked stock
- **AND** it MUST NOT write `CONFIRM` stock records
- **AND** it MUST keep callback handling retryable instead of finalizing the callback as failed, ignored, or processed

#### Scenario: Callback lock releases after transaction
- **WHEN** successful callback processing runs inside an active transaction
- **THEN** the system MUST release the Redis order status lock after transaction completion

### Requirement: Payment Callback Keeps Database Final Guard
The system SHALL keep database status conditions as the final consistency guard for payment callback success.

#### Scenario: Callback final guard applies
- **WHEN** the system updates an order to paid through successful callback processing
- **THEN** the update MUST require the order to still be pending payment in the database

#### Scenario: Late callback final guard prevents paid transition
- **WHEN** the order row is no longer pending payment during successful callback processing
- **THEN** the system MUST NOT update the order to paid
- **AND** it MUST NOT confirm locked stock
