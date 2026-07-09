## ADDED Requirements

### Requirement: System Timeout Can Cancel Pending Orders
The system SHALL allow the system timeout flow to cancel orders that are still pending payment.

#### Scenario: System timeout cancels pending order
- **WHEN** the order timeout consumer processes a pending-payment order after the configured timeout
- **THEN** the order status MUST transition from pending payment to cancelled

#### Scenario: System timeout cannot cancel paid order
- **WHEN** the order timeout consumer processes an order that is no longer pending payment
- **THEN** the system MUST NOT transition that order to cancelled

#### Scenario: Timeout cancellation uses conditional status update
- **WHEN** the system updates an order from pending payment to cancelled through timeout cancellation
- **THEN** the update MUST require the order to still be pending payment in the database

#### Scenario: Timeout cancellation conditional update failure has no stock side effect
- **WHEN** the timeout cancellation conditional status update affects zero rows
- **THEN** the system MUST NOT release locked stock
- **AND** the system MUST NOT write a stock release record
