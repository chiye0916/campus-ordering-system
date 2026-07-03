## MODIFIED Requirements

### Requirement: Redis Lock Protects Order Status Transitions
The system SHALL acquire a Redis distributed lock before executing order status transition logic for payment, cancellation, completion, acceptance, delivery start, refund start, or refund completion.

#### Scenario: Payment obtains order status lock
- **WHEN** a user requests payment for an order
- **THEN** the system MUST acquire `lock:order:status:{orderId}` before creating payment records or updating order status

#### Scenario: Cancellation obtains order status lock
- **WHEN** a user requests cancellation for an order
- **THEN** the system MUST acquire `lock:order:status:{orderId}` before updating order status

#### Scenario: Acceptance obtains order status lock
- **WHEN** an administrator requests acceptance for an order
- **THEN** the system MUST acquire `lock:order:status:{orderId}` before updating order status

#### Scenario: Delivery start obtains order status lock
- **WHEN** an administrator requests delivery start for an order
- **THEN** the system MUST acquire `lock:order:status:{orderId}` before updating order status

#### Scenario: Completion obtains order status lock
- **WHEN** an administrator requests completion for an order
- **THEN** the system MUST acquire `lock:order:status:{orderId}` before updating order status

#### Scenario: Refund start obtains order status lock
- **WHEN** an administrator requests refund start for an order
- **THEN** the system MUST acquire `lock:order:status:{orderId}` before updating order status

#### Scenario: Refund completion obtains order status lock
- **WHEN** an administrator requests refund completion for an order
- **THEN** the system MUST acquire `lock:order:status:{orderId}` before updating order status

### Requirement: Order Status Lock Is Released Safely Around Transactions
The system SHALL release order status locks after transaction completion when Spring transaction synchronization is active, and otherwise release them in a finally path.

#### Scenario: Transactional transition releases lock after completion
- **WHEN** an order status transition runs with active transaction synchronization
- **THEN** the system MUST register a transaction synchronization callback to release `lock:order:status:{orderId}` after transaction completion

#### Scenario: Non-transactional transition releases lock in finally
- **WHEN** an order status transition runs without active transaction synchronization
- **THEN** the system MUST release `lock:order:status:{orderId}` in a finally path

### Requirement: Database Status Conditions Remain Final Guard
The system SHALL keep database status-condition updates as the final consistency guard for every order status transition.

#### Scenario: Payment final guard remains
- **WHEN** the system updates an order from pending payment to paid
- **THEN** the update MUST require the order to still be pending payment in the database

#### Scenario: Cancellation final guard applies
- **WHEN** the system updates an order to cancelled
- **THEN** the update MUST require the order to still be pending payment in the database

#### Scenario: Acceptance final guard applies
- **WHEN** the system updates an order from paid to accepted
- **THEN** the update MUST require the order to still be paid in the database

#### Scenario: Delivery start final guard applies
- **WHEN** the system updates an order from accepted to delivering
- **THEN** the update MUST require the order to still be accepted in the database

#### Scenario: Completion final guard applies
- **WHEN** the system updates an order from delivering to completed
- **THEN** the update MUST require the order to still be delivering in the database

#### Scenario: Refund start from paid final guard applies
- **WHEN** the system updates an order from paid to refunding
- **THEN** the update MUST require the order to still be paid in the database

#### Scenario: Refund start from accepted final guard applies
- **WHEN** the system updates an order from accepted to refunding
- **THEN** the update MUST require the order to still be accepted in the database

#### Scenario: Refund completion final guard applies
- **WHEN** the system updates an order from refunding to refunded
- **THEN** the update MUST require the order to still be refunding in the database

#### Scenario: Paid order cannot be directly cancelled
- **WHEN** a user requests cancellation for a paid order
- **THEN** the system MUST reject the cancellation and MUST NOT update the order to cancelled
