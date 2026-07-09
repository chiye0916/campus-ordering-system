## ADDED Requirements

### Requirement: First Submit Creates Timeout Outbox Transactionally
The system SHALL create the timeout outbox row inside the first successful order submit transaction.

#### Scenario: First submit commits timeout outbox with order
- **WHEN** the first order submission completes successfully
- **THEN** the order, stock lock, order details, cart cleanup, timeout outbox row, and succeeded idempotency record MUST be committed together

#### Scenario: First submit rollback removes timeout outbox
- **WHEN** the first order submission fails before transaction commit
- **THEN** the system MUST NOT leave a committed timeout outbox row for that failed submission

### Requirement: Idempotent Retry Does Not Recreate Timeout Outbox
The system SHALL NOT create or publish another timeout message when idempotent order submission returns an existing order ID.

#### Scenario: Duplicate successful submit returns original order without timeout duplication
- **WHEN** the same logged-in user retries order submission with the same idempotency key and same request fingerprint after success
- **THEN** the system MUST return the original order ID
- **AND** the system MUST NOT create another timeout outbox row
- **AND** the system MUST NOT publish another timeout message for that retry
