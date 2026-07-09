## Purpose

Order submit idempotency prevents duplicate orders when a client retries or submits the same order action more than once.

## Requirements

### Requirement: Submit Requires Idempotency Key
The system SHALL require an `Idempotency-Key` request header for order submission.

#### Scenario: Missing idempotency key is rejected
- **WHEN** a logged-in user submits an order without `Idempotency-Key`
- **THEN** the system MUST reject the request with `400`

#### Scenario: Blank idempotency key is rejected
- **WHEN** a logged-in user submits an order with a blank `Idempotency-Key`
- **THEN** the system MUST reject the request with `400`

### Requirement: First Submit Creates Idempotency Record And Order
The system SHALL create an idempotency record when processing the first order submission for a user and idempotency key.

#### Scenario: First submit succeeds
- **WHEN** a logged-in user submits an order with a new `Idempotency-Key` and a non-empty cart
- **THEN** the system MUST create one idempotency record for the current user and key
- **AND** the system MUST create one order and its order details
- **AND** the system MUST clear the current user's cart
- **AND** the system MUST mark the idempotency record as succeeded with the created order ID
- **AND** the response MUST keep the existing successful `Result<Long>` shape with the created order ID

### Requirement: Duplicate Submit Returns Original Order
The system SHALL return the original order ID when the same user retries the same order submission with the same idempotency key and same request fingerprint.

#### Scenario: Same key and same request after success
- **WHEN** the same logged-in user submits again with the same `Idempotency-Key` and same request fingerprint after the first submit succeeded
- **THEN** the system MUST NOT create another order
- **AND** the system MUST return the original order ID using the existing successful `Result<Long>` shape

#### Scenario: Different user can use same key independently
- **WHEN** another logged-in user submits an order using the same `Idempotency-Key`
- **THEN** the system MUST treat the key as independent for that user

### Requirement: Same Key Different Request Is Rejected
The system SHALL reject reuse of the same idempotency key by the same user when the effective request content differs.

#### Scenario: Same key different request fingerprint
- **WHEN** the same logged-in user submits again with the same `Idempotency-Key` but a different request fingerprint
- **THEN** the system MUST reject the request with `409`
- **AND** the system MUST NOT create another order

### Requirement: Processing Duplicate Is Rejected
The system SHALL reject a duplicate order submission while the first submission for the same user and idempotency key is still processing.

#### Scenario: Same key still processing
- **WHEN** the same logged-in user submits again with the same `Idempotency-Key` while the existing idempotency record is processing
- **THEN** the system MUST reject the request with `409`
- **AND** the system MUST NOT create another order

### Requirement: Request Fingerprint Uses Effective Cart Content
The system SHALL compute the order submit request fingerprint from the current user, submit remark, and effective cart content.

#### Scenario: Fingerprint ignores idempotency key
- **WHEN** the system computes a request fingerprint
- **THEN** the fingerprint MUST NOT include the idempotency key

#### Scenario: Fingerprint includes sorted cart item content
- **WHEN** the system computes a request fingerprint
- **THEN** the fingerprint MUST include the current user ID, remark, and cart items sorted by dish ID with dish ID, quantity, and dish price

### Requirement: Idempotency Persistence Is Transactional With Order Creation
The system SHALL keep order creation and idempotency success marking in one transaction for the first successful submission.

#### Scenario: Order creation transaction succeeds
- **WHEN** the first order submission completes successfully
- **THEN** the order, order details, cart cleanup, and succeeded idempotency record MUST be committed together

#### Scenario: Order creation transaction fails
- **WHEN** order creation fails before the transaction commits
- **THEN** the system MUST NOT leave a succeeded idempotency record pointing to a non-created order

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
