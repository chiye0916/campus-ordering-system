## ADDED Requirements

### Requirement: Timeout Cancellation Releases Locked Stock
The system SHALL release locked stock when automatic timeout cancellation succeeds for a pending-payment order.

#### Scenario: Timeout cancellation releases stock
- **WHEN** a pending-payment order is automatically cancelled by the timeout consumer
- **THEN** the system MUST decrease locked stock for each order detail by its quantity
- **AND** the system MUST increase available stock by the same quantity

#### Scenario: Timeout cancellation aggregates details before release
- **WHEN** timeout cancellation releases stock for an order with multiple details for the same dish
- **THEN** the system MUST aggregate quantities by dish ID and process stock updates in ascending dish ID order

#### Scenario: Timeout cancellation release uses stock guards
- **WHEN** timeout cancellation releases locked stock
- **THEN** the stock update MUST require locked stock to be greater than or equal to the released quantity

#### Scenario: Timeout cancellation release uses row lock records
- **WHEN** timeout cancellation writes stock release records
- **THEN** before and after values MUST be based on a stock row read with a database row lock in the same transaction
