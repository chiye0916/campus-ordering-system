## Purpose

Order timeout cancellation automatically cancels pending-payment orders after the configured timeout and releases locked stock through an idempotent RabbitMQ consumer flow.

## Requirements

### Requirement: RabbitMQ Delays Timeout Cancellation
The system SHALL use RabbitMQ TTL plus Dead Letter Exchange to delay pending-payment order timeout checks.

#### Scenario: Timeout message is delayed
- **WHEN** a first successful order submission creates a pending-payment order
- **THEN** the system MUST publish a timeout message through an outbox to a RabbitMQ delay queue
- **AND** RabbitMQ MUST deliver the message to the timeout cancellation consumer only after the configured timeout expires

#### Scenario: TTL starts after RabbitMQ publish
- **WHEN** RabbitMQ or the outbox publisher is unavailable after order creation
- **THEN** the system MUST NOT guarantee cancellation exactly at the outbox row's expire time
- **AND** the system MUST preserve the expire time for inspection and future compensation

#### Scenario: Default timeout is fifteen minutes
- **WHEN** the application starts without an overridden order timeout value
- **THEN** the order timeout delay MUST default to 15 minutes

### Requirement: Timeout Consumer Cancels Only Pending Orders
The system SHALL automatically cancel an order only when the timeout message is consumed and the order is still pending payment.

#### Scenario: Pending order times out
- **WHEN** the timeout consumer receives a message for an order whose status is pending payment
- **THEN** the system MUST transition the order to cancelled
- **AND** the system MUST set the order cancellation time

#### Scenario: Paid order timeout message is ignored
- **WHEN** the timeout consumer receives a message for an order whose status is paid
- **THEN** the system MUST treat the message as an idempotent no-op
- **AND** the system MUST NOT update the order status
- **AND** the system MUST NOT release stock

#### Scenario: Already cancelled order timeout message is ignored
- **WHEN** the timeout consumer receives a message for an order whose status is already cancelled
- **THEN** the system MUST treat the message as an idempotent no-op
- **AND** the system MUST NOT write another cancellation or stock release

#### Scenario: Missing order timeout message is ignored
- **WHEN** the timeout consumer receives a message for an order ID that does not exist
- **THEN** the system MUST treat the message as stale and complete processing without changing data

### Requirement: Timeout Cancellation Is Transactional
The system SHALL keep timeout cancellation status changes and stock release in one transaction.

#### Scenario: Timeout cancellation commits together
- **WHEN** timeout cancellation succeeds for a pending-payment order
- **THEN** the order cancellation status update and locked stock release MUST commit together

#### Scenario: Stock release failure prevents cancellation
- **WHEN** timeout cancellation updates an order status but locked stock release fails before transaction commit
- **THEN** the order MUST NOT remain cancelled
- **AND** the stock release MUST NOT commit

### Requirement: Timeout Consumer Handles Technical Failures As Retryable
The system SHALL treat technical exceptions during timeout processing as retryable listener failures.

#### Scenario: Technical failure is not acknowledged as business success
- **WHEN** the timeout consumer encounters an unexpected database, Redis, or stock release exception
- **THEN** the listener MUST fail the processing attempt so the messaging layer can retry according to configured retry behavior

#### Scenario: Listener retry is bounded
- **WHEN** the timeout consumer repeatedly fails with a technical exception
- **THEN** the messaging configuration MUST limit retry attempts
- **AND** it MUST use backoff between attempts
- **AND** it MUST avoid infinite immediate requeue loops

#### Scenario: Business no-op is acknowledged
- **WHEN** the timeout consumer finds a non-pending order state
- **THEN** the listener MUST complete processing successfully without retrying the message

#### Scenario: Malformed payload is acknowledged without requeue
- **WHEN** the timeout consumer receives a malformed timeout message payload
- **THEN** the listener MUST log the malformed message
- **AND** it MUST complete processing without requeueing the same malformed message

### Requirement: Timeout Message Uses Order As Business Idempotency Key
The system SHALL use the order ID as the business idempotency key for timeout cancellation.

#### Scenario: Message ID is tracing metadata
- **WHEN** the timeout consumer receives a valid timeout message
- **THEN** the system MUST use order ID to decide whether cancellation is needed
- **AND** it MUST treat message ID as tracing metadata rather than as the business cancellation guard

### Requirement: System Timeout User Is Audit Only
The system SHALL use `system_timeout` as an audit subject for automatic timeout cancellation without granting normal administrator behavior.

#### Scenario: System timeout user is not an admin operator
- **WHEN** the system initializes or resolves the `system_timeout` user
- **THEN** the user role MUST be `SYSTEM`
- **AND** the user MUST NOT be treated as an administrator for normal admin API authorization

#### Scenario: System timeout user is not exposed in frontend presets
- **WHEN** the frontend presents login presets or role-specific app entry points
- **THEN** it MUST NOT expose `system_timeout` as a normal human login preset

#### Scenario: System timeout user cannot login normally
- **WHEN** a `SYSTEM` user attempts to authenticate through the normal login API
- **THEN** the system MUST reject the login
