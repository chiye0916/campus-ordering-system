## ADDED Requirements

### Requirement: Idempotent Retry Does Not Relock Stock
The system SHALL NOT lock stock again when an idempotent retry returns an existing order ID.

#### Scenario: Duplicate successful submit returns original order without stock change
- **WHEN** the same logged-in user retries order submission with the same `Idempotency-Key` and same request fingerprint after the first submit succeeded
- **THEN** the system MUST return the original order ID
- **AND** the system MUST NOT create another order
- **AND** the system MUST NOT lock stock again
- **AND** the system MUST NOT write additional `LOCK` stock records for that retry

### Requirement: First Submit Stock Lock Is Transactional With Idempotency
The system SHALL keep stock locking in the same transaction as first-submit order creation and idempotency success marking.

#### Scenario: First submit commits stock lock with order
- **WHEN** the first order submission completes successfully
- **THEN** the stock lock, `LOCK` stock records, order, order details, cart cleanup, and succeeded idempotency record MUST be committed together

#### Scenario: First submit obtains order ID before lock record
- **WHEN** the first order submission needs to write `LOCK` stock records
- **THEN** the system MUST create the pending-payment order main record first to obtain an order ID
- **AND** the system MUST write `LOCK` stock records linked to that order ID in the same transaction

#### Scenario: First submit rolls back stock lock on failure
- **WHEN** the first order submission fails before transaction commit
- **THEN** the system MUST NOT leave committed stock locks or `LOCK` stock records for the failed submission

#### Scenario: Partial stock lock failure rolls back all locked stock
- **WHEN** stock locking succeeds for one dish but fails for another dish in the same order submission
- **THEN** the system MUST roll back all stock locks and `LOCK` stock records for that order submission
- **AND** the system MUST NOT leave the pending-payment order committed
