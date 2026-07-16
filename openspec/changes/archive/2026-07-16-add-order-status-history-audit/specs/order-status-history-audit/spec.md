## ADDED Requirements

### Requirement: Order Status History Records
The system SHALL persist successful order status changes as order status history records.

#### Scenario: History record stores status transition snapshot
- **WHEN** an order status history record is created
- **THEN** the system MUST store order id, order number, order owner user id, old status, new status, operation, operator role, reason, trace id, and creation time
- **AND** it MUST store `old_status` and `new_status` as integer status codes compatible with `orders.status`

#### Scenario: History field constraints are explicit
- **WHEN** the order status history table is created
- **THEN** `old_status`, `operator_id`, and `trace_id` MUST allow null
- **AND** `new_status`, `operator_role`, `reason`, and `create_time` MUST be required

#### Scenario: Order creation stores nullable old status
- **WHEN** an order is created successfully
- **THEN** the system MUST create an order status history record with `old_status = null`
- **AND** it MUST set `new_status` to pending payment
- **AND** it MUST set operation to `ORDER_SUBMIT`

#### Scenario: History keeps chronological query support
- **WHEN** order status history is persisted
- **THEN** the database design MUST support efficient lookup by order id ordered by creation time and id
- **AND** it MUST support trace id lookup for diagnostics

### Requirement: Order Status Change Operations
The system SHALL classify each recorded order status history entry with a stable operation.

#### Scenario: Supported operations are explicit
- **WHEN** the system records an order status history entry
- **THEN** it MUST use one of `ORDER_SUBMIT`, `PAYMENT_SUCCESS`, `USER_CANCEL`, `TIMEOUT_CANCEL`, `MERCHANT_ACCEPT`, `DELIVERY_START`, `DELIVERY_COMPLETE`, `REFUND_REQUEST_APPROVE`, `REFUND_REQUEST_COMPLETE`, `INTERNAL_REFUND_START`, or `INTERNAL_REFUND_COMPLETE`

#### Scenario: Operation has default Chinese reason
- **WHEN** the system records an order status history entry without a more specific reason
- **THEN** it MUST use the operation's default Chinese business reason

#### Scenario: Specific reason can extend default meaning
- **WHEN** a status change has useful business context such as a refund number
- **THEN** the system MAY store a more specific reason
- **AND** the reason MUST still describe the operation in business-readable Chinese

### Requirement: Order Status History Transactionality
The system SHALL write order status history in the same transaction as the related successful order status change.

#### Scenario: Successful status change writes history in transaction
- **WHEN** a service method successfully changes an order status
- **THEN** it MUST write the corresponding order status history record before the transaction commits

#### Scenario: History service uses caller transaction
- **WHEN** `recordChange` is called for a status-changing flow
- **THEN** it MUST participate in the caller's transaction
- **AND** it MUST NOT use a new independent transaction

#### Scenario: History insertion failure rolls back status change
- **WHEN** order status history insertion fails during a status-changing operation
- **THEN** the related order status change MUST roll back

#### Scenario: Failed conditional update does not write history
- **WHEN** a conditional order status update affects zero rows
- **THEN** the system MUST NOT write an order status history record for that attempted change

#### Scenario: No-op flow does not write history
- **WHEN** a duplicate callback, timeout no-op, or other no-op flow does not change order status
- **THEN** the system MUST NOT write an order status history record

### Requirement: Operator Attribution
The system SHALL attribute each order status history record to the actor that caused the status change.

#### Scenario: User action records user operator
- **WHEN** a `USER` action changes order status
- **THEN** the history record MUST store that user's id as operator id
- **AND** it MUST store `USER` as operator role

#### Scenario: Merchant action records merchant operator
- **WHEN** a `MERCHANT` action changes order status
- **THEN** the history record MUST store that merchant user's id as operator id
- **AND** it MUST store `MERCHANT` as operator role

#### Scenario: Delivery action records delivery operator
- **WHEN** a `DELIVERY` action changes order status
- **THEN** the history record MUST store that delivery user's id as operator id
- **AND** it MUST store `DELIVERY` as operator role

#### Scenario: Admin action records admin operator
- **WHEN** an `ADMIN` action changes order status
- **THEN** the history record MUST store that admin user's id as operator id
- **AND** it MUST store `ADMIN` as operator role

#### Scenario: System action records system role
- **WHEN** a system-triggered flow changes order status
- **THEN** the history record MUST store `SYSTEM` as operator role
- **AND** it SHOULD use the existing `system_timeout` user id as operator id when it can be resolved
- **AND** it MAY store a null operator id when the system audit user cannot be resolved

### Requirement: Trace Correlation
The system SHALL associate order status history entries with the current trace id when available.

#### Scenario: HTTP status change stores trace id
- **WHEN** an HTTP request changes order status while a trace id exists
- **THEN** the history record MUST store that trace id

#### Scenario: Message-driven status change stores trace id
- **WHEN** a message-driven flow changes order status while a trace id exists
- **THEN** the history record MUST store that trace id

#### Scenario: Async flow reuses existing trace context
- **WHEN** an async or system flow changes order status and the listener or scheduler has placed a trace id in the current trace context
- **THEN** the history record MUST use that trace id

#### Scenario: Missing trace id does not block history
- **WHEN** a status-changing flow has no current trace id
- **THEN** the system MUST still write order status history
- **AND** the history record MAY store a null trace id

### Requirement: Order Status History Query
The system SHALL expose a status history timeline for visible orders.

#### Scenario: Visible order history is returned
- **WHEN** a logged-in actor requests `GET /order/{id}/status-history` for an order they can view through order detail visibility rules
- **THEN** the system MUST return that order's status history records
- **AND** it MUST order them by `create_time asc, id asc`

#### Scenario: Visible order with no history returns empty list
- **WHEN** a logged-in actor requests status history for a visible order that has no history records
- **THEN** the system MUST return an empty list

#### Scenario: User cannot query another user's hidden order history
- **WHEN** a `USER` requests status history for an order they do not own
- **THEN** the system MUST reject the request as if the order does not exist for that user

#### Scenario: Merchant history visibility follows order detail visibility
- **WHEN** a `MERCHANT` requests status history for an order visible to merchant order detail scope
- **THEN** the system MUST return the status history records

#### Scenario: Delivery history visibility follows order detail visibility
- **WHEN** a `DELIVERY` requests status history for an order visible to delivery order detail scope
- **THEN** the system MUST return the status history records

#### Scenario: Admin can query any order history
- **WHEN** an `ADMIN` requests status history for any existing order
- **THEN** the system MUST return the status history records

#### Scenario: System cannot query through normal HTTP
- **WHEN** a `SYSTEM` role reaches the normal HTTP status history endpoint
- **THEN** the system MUST reject the request with a permission error

### Requirement: Non-Goals Are Excluded From History
The system SHALL keep first-version order status history focused on successful order status transitions.

#### Scenario: Failed attempts are not recorded
- **WHEN** an operation fails validation, permission, state-machine, or conditional update checks
- **THEN** the system MUST NOT write an order status history record for that failed attempt

#### Scenario: Non-status business actions are not recorded
- **WHEN** an action does not change order status, such as cart changes, catalog changes, stock SET, refund request rejection, or payment failure callback
- **THEN** the system MUST NOT write an order status history record for that action

#### Scenario: Business-specific foreign keys are not required
- **WHEN** an order status history record is written
- **THEN** it MUST NOT require payment record id, refund request id, timeout message id, or other business-object-specific foreign keys in this version

#### Scenario: Existing orders are not backfilled
- **WHEN** the order status history feature is introduced
- **THEN** the system MUST NOT require backfilling status history for orders created before this feature
- **AND** it MUST start recording history for new successful status changes after implementation
