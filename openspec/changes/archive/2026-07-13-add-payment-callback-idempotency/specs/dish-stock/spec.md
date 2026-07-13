## ADDED Requirements

### Requirement: Payment Callback Confirms Locked Stock
The system SHALL confirm locked stock consumption when a valid successful mock payment callback pays an order.

#### Scenario: Callback consumes locked stock
- **WHEN** a successful mock payment callback transitions a pending-payment order to paid
- **THEN** the system MUST decrease locked stock for each order detail by its quantity
- **AND** the system MUST NOT change available stock

#### Scenario: Callback aggregates details before stock confirmation
- **WHEN** callback processing confirms stock for an order with multiple details for the same dish
- **THEN** the system MUST aggregate quantities by dish ID and process stock updates in ascending dish ID order

#### Scenario: Callback rolls back on stock confirmation failure
- **WHEN** locked stock confirmation fails during successful callback processing
- **THEN** the system MUST reject or fail the payment success processing
- **AND** the order MUST NOT be marked paid
- **AND** the payment record MUST NOT remain successful for that callback

### Requirement: Duplicate Or Late Callback Does Not Change Stock
The system SHALL avoid stock side effects for duplicate callbacks or callbacks that cannot transition the order to paid.

#### Scenario: Duplicate successful callback does not consume stock again
- **WHEN** a duplicate successful callback is received for an already successful trade
- **THEN** the system MUST NOT decrease locked stock again

#### Scenario: Late successful callback after cancellation does not consume stock
- **WHEN** a successful callback is received after the order has been cancelled
- **THEN** the system MUST NOT decrease locked stock
- **AND** it MUST NOT change available stock

#### Scenario: Failed callback does not change stock
- **WHEN** a failed callback is processed
- **THEN** the system MUST NOT decrease locked stock
- **AND** it MUST NOT increase available stock
