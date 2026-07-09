## ADDED Requirements

### Requirement: Timeout Outbox Stores Send Intent
The system SHALL persist the intent to send an order timeout message in an outbox table.

#### Scenario: Outbox row is created with order
- **WHEN** a first successful order submission creates a pending-payment order
- **THEN** the system MUST create one `order_timeout_outbox` row for the created order in the same transaction

#### Scenario: Outbox stores message identity and payload
- **WHEN** the system creates an order timeout outbox row
- **THEN** the row MUST include order ID, unique message ID, payload, expire time, status, retry count, next retry time, publish claim time, create time, and update time

#### Scenario: One timeout outbox per order
- **WHEN** the system creates an order timeout outbox row
- **THEN** the database MUST enforce at most one timeout outbox row per order

#### Scenario: Outbox row rolls back with order
- **WHEN** order submission fails before transaction commit
- **THEN** the system MUST NOT leave a committed timeout outbox row for that failed order

### Requirement: Idempotent Retry Does Not Duplicate Timeout Outbox
The system SHALL NOT create duplicate timeout outbox rows when order submission returns an existing order ID through idempotency.

#### Scenario: Duplicate successful submit does not create outbox
- **WHEN** a user retries order submission with the same idempotency key and same request fingerprint after success
- **THEN** the system MUST return the original order ID
- **AND** the system MUST NOT create another timeout outbox row
- **AND** the system MUST NOT send another timeout message for that retry

### Requirement: Outbox Publisher Sends Pending Messages
The system SHALL publish due timeout outbox messages to RabbitMQ from a scheduled publisher.

#### Scenario: Publisher claims pending message before publish
- **WHEN** an outbox row has pending status and its next retry time is due
- **THEN** the publisher MUST conditionally claim the row by changing its status to publishing before sending
- **AND** the claim condition MUST require the row's next retry time to still be due
- **AND** the claim condition MUST require retry count to be below the configured maximum
- **AND** the claim MUST set publish claim time

#### Scenario: Claimed message is published
- **WHEN** an outbox row is successfully claimed for publishing
- **THEN** the publisher MUST send the payload to the configured RabbitMQ timeout delay exchange

#### Scenario: Unclaimed message is skipped
- **WHEN** an outbox row cannot be claimed because another publisher changed its status first
- **THEN** the publisher MUST NOT send that row's timeout message

#### Scenario: Confirmed publish marks sent
- **WHEN** RabbitMQ publisher confirm acknowledges a timeout message publish
- **THEN** the system MUST mark the outbox row as sent
- **AND** the system MUST set sent time

#### Scenario: Publish failure records retry state
- **WHEN** RabbitMQ publishing fails, is negatively acknowledged, or times out before confirmation
- **THEN** the system MUST record the failure reason
- **AND** the system MUST increase retry count
- **AND** the system MUST schedule a future retry unless the maximum retry count has been reached

#### Scenario: Maximum retries leave failed row
- **WHEN** an outbox row reaches the configured maximum retry count
- **THEN** the system MUST keep the row in failed status with the last error for manual inspection

### Requirement: Outbox Publisher Recovers Stale Publishing Claims
The system SHALL recover timeout outbox rows that stay in publishing status beyond the configured claim timeout.

#### Scenario: Stale publishing row becomes retryable
- **WHEN** an outbox row is in publishing status longer than the configured claim timeout
- **THEN** the publisher MUST make the row retryable again
- **AND** it MUST NOT leave the row permanently stuck in publishing status

#### Scenario: Fresh publishing row is not recovered
- **WHEN** an outbox row is in publishing status but its publish claim time is still within the configured claim timeout
- **THEN** the publisher MUST NOT recover or republish that row

### Requirement: Outbox Payload Is Minimal
The system SHALL put only stable identifiers and timing data in the timeout message payload.

#### Scenario: Payload omits mutable order details
- **WHEN** the timeout outbox payload is created
- **THEN** it MUST include order ID, message ID, and expire time
- **AND** it MUST NOT rely on mutable order detail or stock values from the message payload

### Requirement: Timeout Outbox Provides At-Least-Once Delivery
The system SHALL treat timeout message publishing as at-least-once delivery.

#### Scenario: Confirmed message can be retried if sent marking fails
- **WHEN** RabbitMQ confirms a timeout message but the database update that marks the outbox row sent fails
- **THEN** the system MUST tolerate publishing the timeout message again on a later retry
- **AND** the consumer MUST rely on idempotent order-state handling rather than exactly-once message delivery

#### Scenario: Message ID is used for tracing
- **WHEN** the publisher sends a timeout message
- **THEN** the system MUST use the message ID as the publisher confirm correlation identifier when the messaging API supports it
