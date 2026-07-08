## ADDED Requirements

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
