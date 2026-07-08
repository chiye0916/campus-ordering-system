## ADDED Requirements

### Requirement: Dish Stock Table
The system SHALL store dish inventory in a dedicated dish stock table separate from dish product data.

#### Scenario: Dish stock stores available and locked quantities
- **WHEN** dish stock exists for a dish
- **THEN** it MUST include dish ID, available stock, locked stock, version, create time, and update time

#### Scenario: Dish stock belongs to one dish
- **WHEN** a dish stock row is created
- **THEN** the system MUST enforce one stock row per dish

#### Scenario: Dish stock quantities are non-negative
- **WHEN** the system creates or updates a dish stock row
- **THEN** available stock and locked stock MUST NOT become negative

### Requirement: Administrator Manages Dish Stock
The system SHALL allow an administrator to query and set dish stock separately from dish creation or dish update.

#### Scenario: Administrator queries stock
- **WHEN** an administrator queries stock for a dish
- **THEN** the system MUST return available stock, locked stock, and version for that dish

#### Scenario: Administrator sets available stock
- **WHEN** an administrator sets available stock for a dish
- **THEN** the system MUST update the dish stock available quantity to the requested non-negative value
- **AND** the system MUST NOT silently clear locked stock

#### Scenario: Administrator set creates missing stock row
- **WHEN** an administrator sets available stock for an existing dish that has no stock row
- **THEN** the system MUST create a dish stock row for that dish

#### Scenario: Administrator set rejects missing dish
- **WHEN** an administrator sets available stock for a dish that does not exist
- **THEN** the system MUST reject the request with a dish-not-found business error

#### Scenario: Stock query rejects missing stock row
- **WHEN** an administrator queries stock for a dish that has no stock row
- **THEN** the system MUST reject the request as stock not initialized

#### Scenario: Non-admin cannot manage stock
- **WHEN** a non-admin user attempts to set dish stock
- **THEN** the system MUST reject the request with a permission error

### Requirement: Order Submit Locks Stock
The system SHALL lock dish stock during the first successful order submission.

#### Scenario: Submit locks available stock
- **WHEN** a logged-in user submits an order with sufficient available stock
- **THEN** the system MUST decrease available stock by the submitted quantity
- **AND** the system MUST increase locked stock by the submitted quantity

#### Scenario: Submit aggregates duplicate dish items
- **WHEN** order submission contains multiple cart items for the same dish
- **THEN** the system MUST aggregate quantities by dish ID before locking stock

#### Scenario: Submit locks in stable dish order
- **WHEN** order submission locks stock for multiple dishes
- **THEN** the system MUST process stock updates in ascending dish ID order

#### Scenario: Submit rejects insufficient stock
- **WHEN** a logged-in user submits an order and any dish has insufficient available stock
- **THEN** the system MUST reject the order submission
- **AND** the system MUST NOT create an order
- **AND** the system MUST NOT leave partial stock locks

#### Scenario: Submit rejects missing stock row
- **WHEN** a logged-in user submits an order for a dish without a stock row
- **THEN** the system MUST reject the order submission
- **AND** the system MUST NOT create an order

### Requirement: Payment Confirms Locked Stock
The system SHALL confirm locked stock consumption when mock payment succeeds.

#### Scenario: Payment consumes locked stock
- **WHEN** a pending-payment order is paid successfully
- **THEN** the system MUST decrease locked stock for each order detail by its quantity
- **AND** the system MUST NOT change available stock

#### Scenario: Payment aggregates details before stock confirmation
- **WHEN** payment confirms stock for an order with multiple details for the same dish
- **THEN** the system MUST aggregate quantities by dish ID and process stock updates in ascending dish ID order

#### Scenario: Payment rolls back on stock confirmation failure
- **WHEN** locked stock confirmation fails during payment
- **THEN** the system MUST reject the payment
- **AND** the order MUST NOT be marked paid

### Requirement: Pending Cancellation Releases Locked Stock
The system SHALL release locked stock when a pending-payment order is cancelled.

#### Scenario: Cancel releases locked stock
- **WHEN** a pending-payment order is cancelled
- **THEN** the system MUST decrease locked stock for each order detail by its quantity
- **AND** the system MUST increase available stock by the same quantity

#### Scenario: Cancel aggregates details before stock release
- **WHEN** cancellation releases stock for an order with multiple details for the same dish
- **THEN** the system MUST aggregate quantities by dish ID and process stock updates in ascending dish ID order

#### Scenario: Cancel rolls back on stock release failure
- **WHEN** locked stock release fails during cancellation
- **THEN** the system MUST reject the cancellation
- **AND** the order MUST NOT be marked cancelled

### Requirement: Stock Updates Use Database Guards
The system SHALL use database conditional updates as the stock consistency guard.

#### Scenario: Lock uses available stock guard
- **WHEN** stock is locked during order submission
- **THEN** the stock update MUST require available stock to be greater than or equal to the requested quantity

#### Scenario: Stock row is locked for accurate records
- **WHEN** the system performs a stock-changing operation that writes a stock record
- **THEN** it MUST read the dish stock row with a database row lock in the same transaction before writing the stock record

#### Scenario: Confirm uses locked stock guard
- **WHEN** locked stock is confirmed during payment
- **THEN** the stock update MUST require locked stock to be greater than or equal to the requested quantity

#### Scenario: Release uses locked stock guard
- **WHEN** locked stock is released during cancellation
- **THEN** the stock update MUST require locked stock to be greater than or equal to the requested quantity
