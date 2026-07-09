## ADDED Requirements

### Requirement: Timeout Cancellation Uses Order Status Lock
The system SHALL acquire the Redis order status lock before executing automatic timeout cancellation logic.

#### Scenario: Timeout cancellation obtains order status lock
- **WHEN** the timeout consumer attempts to cancel an order
- **THEN** the system MUST acquire `lock:order:status:{orderId}` before updating order status or releasing stock

#### Scenario: Timeout lock acquisition failure is retryable
- **WHEN** timeout cancellation cannot acquire the order status lock because another transition is in progress
- **THEN** the system MUST reject the current processing attempt as retryable
- **AND** it MUST NOT update the order status
- **AND** it MUST NOT release stock

#### Scenario: Timeout lock failure is not a business no-op
- **WHEN** timeout cancellation cannot acquire the order status lock
- **THEN** the system MUST NOT acknowledge the message as an idempotent business success

#### Scenario: Timeout cancellation lock releases after transaction
- **WHEN** timeout cancellation runs inside an active transaction
- **THEN** the system MUST release the Redis order status lock after transaction completion

### Requirement: Timeout Cancellation Keeps Database Final Guard
The system SHALL keep database status conditions as the final consistency guard for timeout cancellation.

#### Scenario: Timeout final guard applies
- **WHEN** the system updates an order to cancelled through timeout cancellation
- **THEN** the update MUST require the order to still be pending payment in the database
