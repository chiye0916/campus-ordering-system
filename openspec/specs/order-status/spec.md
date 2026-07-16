## Purpose

Order status defines the explicit lifecycle states, allowed transitions, and actor permissions for order operations.

## Requirements

### Requirement: Expanded Order Statuses
The system SHALL support explicit order statuses for pending payment, paid, accepted, delivering, completed, cancelled, refunding, and refunded orders.

#### Scenario: Order status code mapping
- **WHEN** the system stores or returns an order status
- **THEN** it MUST use `1` for pending payment, `2` for paid, `3` for completed, `4` for cancelled, `5` for accepted, `6` for delivering, `7` for refunding, and `8` for refunded

#### Scenario: Numeric status order is not lifecycle order
- **WHEN** the system evaluates lifecycle progress or legal status transitions
- **THEN** it MUST use `OrderStatus` transition rules and MUST NOT infer lifecycle order from numeric status comparisons

#### Scenario: New order starts pending payment
- **WHEN** a user submits an order successfully
- **THEN** the order status MUST be pending payment

### Requirement: Legal Order State Transitions
The system SHALL allow only state-machine-approved order status transitions.

#### Scenario: Pending order can be paid by successful callback
- **WHEN** a valid successful mock payment callback is processed for a pending-payment order
- **THEN** the order status MUST transition from pending payment to paid

#### Scenario: Pending order can be cancelled
- **WHEN** a `USER` cancels their own pending-payment order
- **THEN** the order status MUST transition from pending payment to cancelled

#### Scenario: Paid order can be accepted
- **WHEN** a `MERCHANT` or `ADMIN` accepts a paid order
- **THEN** the order status MUST transition from paid to accepted

#### Scenario: Accepted order can start delivery
- **WHEN** a `DELIVERY` or `ADMIN` starts delivery for an accepted order
- **THEN** the order status MUST transition from accepted to delivering

#### Scenario: Delivering order can be completed
- **WHEN** a `DELIVERY` or `ADMIN` completes a delivering order
- **THEN** the order status MUST transition from delivering to completed

#### Scenario: Paid order can enter refunding through approved refund request
- **WHEN** an `ADMIN` approves a pending refund request for a paid order
- **THEN** the order status MUST transition from paid to refunding

#### Scenario: Accepted order can enter refunding through approved refund request
- **WHEN** an `ADMIN` approves a pending refund request for an accepted order
- **THEN** the order status MUST transition from accepted to refunding

#### Scenario: Paid order can enter refunding through internal fallback
- **WHEN** an `ADMIN` starts an internal mock refund for a paid order through the order-level fallback endpoint
- **THEN** the order status MUST transition from paid to refunding

#### Scenario: Accepted order can enter refunding through internal fallback
- **WHEN** an `ADMIN` starts an internal mock refund for an accepted order through the order-level fallback endpoint
- **THEN** the order status MUST transition from accepted to refunding

#### Scenario: Refunding order can become refunded through completed refund request
- **WHEN** an `ADMIN` completes an approved refund request for a refunding order
- **THEN** the order status MUST transition from refunding to refunded

#### Scenario: Refunding order can become refunded through internal fallback
- **WHEN** an `ADMIN` completes an internal mock refund for a refunding order through the order-level fallback endpoint
- **THEN** the order status MUST transition from refunding to refunded

### Requirement: Payment Initiation Does Not Change Order State
The system SHALL not change order business status when mock payment is only initiated.

#### Scenario: Payment initiation keeps pending status
- **WHEN** a user calls `PUT /order/{id}/pay` for a pending-payment order
- **THEN** the order MUST remain pending payment until a valid successful callback is processed

### Requirement: Illegal Order State Transitions Are Rejected
The system SHALL reject attempts to perform order status transitions that are not allowed by the state machine.

#### Scenario: Cancelled order cannot be paid
- **WHEN** a user pays a cancelled order
- **THEN** the system MUST reject the payment and MUST NOT update the order status

#### Scenario: Completed order cannot be cancelled
- **WHEN** a user cancels a completed order
- **THEN** the system MUST reject the cancellation and MUST NOT update the order status

#### Scenario: Accepted order cannot be accepted again
- **WHEN** a `MERCHANT` or `ADMIN` accepts an already accepted order
- **THEN** the system MUST reject the request and MUST NOT update the order status

#### Scenario: Paid order cannot be completed directly
- **WHEN** a `DELIVERY` or `ADMIN` completes a paid order before acceptance and delivery
- **THEN** the system MUST reject the request and MUST NOT update the order status

#### Scenario: Completed order cannot be refunded in first version
- **WHEN** an `ADMIN` starts a refund for a completed order
- **THEN** the system MUST reject the request and MUST NOT update the order status

#### Scenario: Delivering order cannot be refunded in first version
- **WHEN** an `ADMIN` starts a refund for a delivering order
- **THEN** the system MUST reject the request and MUST NOT update the order status

#### Scenario: User refund request cannot target ineligible order state
- **WHEN** a user requests a refund for an order whose status is not paid or accepted
- **THEN** the system MUST reject the refund request
- **AND** it MUST NOT update the order status

#### Scenario: Refund request approval cannot refund changed order state
- **WHEN** an `ADMIN` approves a refund request for an order that is no longer paid or accepted
- **THEN** the system MUST reject the approval
- **AND** it MUST NOT update the order status

#### Scenario: Refund request completion cannot complete changed order state
- **WHEN** an `ADMIN` completes a refund request for an order that is no longer refunding
- **THEN** the system MUST reject the completion
- **AND** it MUST NOT update the order status

### Requirement: Actor Permissions For Order Transitions
The system SHALL restrict order status transition operations by actor role and order ownership.

#### Scenario: USER pays own order
- **WHEN** a logged-in `USER` pays an order they own
- **THEN** the system MUST allow payment initiation only if the order is pending payment

#### Scenario: Non-USER cannot initiate customer payment
- **WHEN** a `MERCHANT`, `DELIVERY`, `ADMIN`, or `SYSTEM` attempts to initiate mock payment through the customer payment endpoint
- **THEN** the system MUST reject the request with a permission error
- **AND** it MUST NOT update payment or order state

#### Scenario: USER cannot pay another user's order
- **WHEN** a logged-in `USER` pays an order owned by another user
- **THEN** the system MUST reject the request as if the order does not exist for that user

#### Scenario: USER cancels own pending order
- **WHEN** a logged-in `USER` cancels an order they own
- **THEN** the system MUST allow the cancellation only if the order is pending payment

#### Scenario: Non-USER cannot use customer cancel
- **WHEN** a `MERCHANT`, `DELIVERY`, `ADMIN`, or `SYSTEM` attempts to cancel an order through the customer cancel endpoint
- **THEN** the system MUST reject the request with a permission error
- **AND** it MUST NOT update order status

#### Scenario: MERCHANT accepts paid order
- **WHEN** a `MERCHANT` accepts a paid order
- **THEN** the system MUST authorize the operation before executing the paid-to-accepted transition

#### Scenario: DELIVERY starts delivery
- **WHEN** a `DELIVERY` starts delivery for an accepted order
- **THEN** the system MUST authorize the operation before executing the accepted-to-delivering transition

#### Scenario: DELIVERY completes delivering order
- **WHEN** a `DELIVERY` completes a delivering order
- **THEN** the system MUST authorize the operation before executing the delivering-to-completed transition

#### Scenario: ADMIN performs platform fallback transitions
- **WHEN** an `ADMIN` accepts an order, starts delivery, completes an order, starts a refund, or completes a refund
- **THEN** the system MUST authorize the operation before executing the status transition as a platform fallback operator

#### Scenario: Refund transitions are ADMIN only
- **WHEN** a `USER`, `MERCHANT`, `DELIVERY`, or `SYSTEM` attempts to start or complete an internal mock refund
- **THEN** the system MUST reject the request with a permission error
- **AND** it MUST NOT update order status

#### Scenario: Unauthorized role cannot perform fulfillment transitions
- **WHEN** a role other than the allowed role for accept, start delivery, complete, refund start, or refund complete attempts that transition
- **THEN** the system MUST reject the request with a permission error
- **AND** it MUST NOT update the order status

### Requirement: Order Status Persistence Uses Conditional Updates
The system SHALL persist every order status transition with a database condition that requires the expected previous status.

#### Scenario: Conditional update succeeds for expected status
- **WHEN** the order row still has the expected previous status during a transition
- **THEN** the system MUST update the row to the new status

#### Scenario: Conditional update fails for changed status
- **WHEN** the order row no longer has the expected previous status during a transition
- **THEN** the system MUST reject the transition and MUST NOT report success

### Requirement: Payment Confirms Stock Consumption
The system SHALL confirm locked stock consumption as part of successful mock payment callback processing.

#### Scenario: Successful callback paid transition confirms stock
- **WHEN** a successful mock payment callback transitions a pending-payment order to paid
- **THEN** the system MUST confirm locked stock consumption for each order detail in the same transaction

#### Scenario: Callback confirms stock only after status update succeeds
- **WHEN** the conditional update from pending payment to paid affects one row during successful callback processing
- **THEN** the system MUST confirm locked stock consumption and write `CONFIRM` stock records

#### Scenario: Duplicate callback does not confirm stock twice
- **WHEN** the conditional update from pending payment to paid affects zero rows during callback processing
- **THEN** the system MUST NOT confirm locked stock consumption
- **AND** the system MUST NOT write `CONFIRM` stock records

#### Scenario: Callback stock confirmation failure prevents paid status
- **WHEN** stock confirmation fails during successful callback processing
- **THEN** the order MUST NOT transition to paid

### Requirement: Pending Cancellation Releases Stock
The system SHALL release locked stock as part of pending-payment order cancellation.

#### Scenario: Pending cancellation releases stock
- **WHEN** a pending-payment order transitions to cancelled
- **THEN** the system MUST release locked stock for each order detail in the same transaction

#### Scenario: Cancellation releases stock only after status update succeeds
- **WHEN** the conditional update from pending payment to cancelled affects one row
- **THEN** the system MUST release locked stock and write `RELEASE` stock records

#### Scenario: Duplicate cancellation does not release stock twice
- **WHEN** the conditional update from pending payment to cancelled affects zero rows
- **THEN** the system MUST NOT release locked stock
- **AND** the system MUST NOT write `RELEASE` stock records

#### Scenario: Cancellation stock release failure prevents cancelled status
- **WHEN** stock release fails during cancellation
- **THEN** the order MUST NOT transition to cancelled

### Requirement: Non-Stock Status Transitions Do Not Change Stock
The system SHALL not change stock during order status transitions that do not represent submit, payment confirmation, or pending-payment cancellation.

#### Scenario: Accept does not change stock
- **WHEN** a `MERCHANT` or `ADMIN` accepts an order
- **THEN** the system MUST NOT change dish stock

#### Scenario: Delivery and completion do not change stock
- **WHEN** a `DELIVERY` or `ADMIN` starts delivery or completes an order
- **THEN** the system MUST NOT change dish stock

#### Scenario: Mock refund transitions do not change stock
- **WHEN** an `ADMIN` starts or completes an internal mock refund
- **THEN** the system MUST NOT change dish stock

#### Scenario: Refund request transitions do not change stock
- **WHEN** an `ADMIN` approves or completes a refund request
- **THEN** the system MUST NOT change dish stock

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

### Requirement: Successful Status Transitions Write History
The system SHALL write order status history for every successful order status transition.

#### Scenario: Order submit writes creation history
- **WHEN** an order is submitted successfully and starts pending payment
- **THEN** the system MUST write an order status history record with operation `ORDER_SUBMIT`
- **AND** the record MUST have `old_status = null` and `new_status = PENDING_PAYMENT`

#### Scenario: Payment success writes paid history
- **WHEN** a valid successful mock payment callback transitions an order from pending payment to paid
- **THEN** the system MUST write an order status history record with operation `PAYMENT_SUCCESS`

#### Scenario: User cancellation writes cancelled history
- **WHEN** a `USER` cancels their own pending-payment order and the order transitions to cancelled
- **THEN** the system MUST write an order status history record with operation `USER_CANCEL`

#### Scenario: Timeout cancellation writes cancelled history
- **WHEN** the system timeout flow transitions a pending-payment order to cancelled
- **THEN** the system MUST write an order status history record with operation `TIMEOUT_CANCEL`

#### Scenario: Merchant accept writes accepted history
- **WHEN** a `MERCHANT` or `ADMIN` accepts a paid order and the order transitions to accepted
- **THEN** the system MUST write an order status history record with operation `MERCHANT_ACCEPT`

#### Scenario: Delivery start writes delivering history
- **WHEN** a `DELIVERY` or `ADMIN` starts delivery for an accepted order and the order transitions to delivering
- **THEN** the system MUST write an order status history record with operation `DELIVERY_START`

#### Scenario: Delivery complete writes completed history
- **WHEN** a `DELIVERY` or `ADMIN` completes a delivering order and the order transitions to completed
- **THEN** the system MUST write an order status history record with operation `DELIVERY_COMPLETE`

#### Scenario: Refund request approval writes refunding history
- **WHEN** an `ADMIN` approves a pending refund request and the order transitions from paid or accepted to refunding
- **THEN** the system MUST write an order status history record with operation `REFUND_REQUEST_APPROVE`

#### Scenario: Refund request completion writes refunded history
- **WHEN** an `ADMIN` completes an approved refund request and the order transitions from refunding to refunded
- **THEN** the system MUST write an order status history record with operation `REFUND_REQUEST_COMPLETE`

#### Scenario: Internal refund start writes refunding history
- **WHEN** an `ADMIN` starts an internal mock refund through the order-level fallback endpoint and the order transitions to refunding
- **THEN** the system MUST write an order status history record with operation `INTERNAL_REFUND_START`

#### Scenario: Internal refund completion writes refunded history
- **WHEN** an `ADMIN` completes an internal mock refund through the order-level fallback endpoint and the order transitions to refunded
- **THEN** the system MUST write an order status history record with operation `INTERNAL_REFUND_COMPLETE`

### Requirement: Failed Or No-Op Transitions Do Not Write History
The system SHALL NOT write order status history when no order status transition occurs.

#### Scenario: Duplicate payment callback does not write duplicate history
- **WHEN** a duplicate successful mock payment callback is processed and the order status does not change
- **THEN** the system MUST NOT write another `PAYMENT_SUCCESS` order status history record

#### Scenario: Timeout cancellation no-op does not write history
- **WHEN** the timeout cancellation flow processes an order that is no longer pending payment
- **THEN** the system MUST NOT write a `TIMEOUT_CANCEL` order status history record

#### Scenario: Conditional update failure does not write history
- **WHEN** a conditional order status update affects zero rows
- **THEN** the system MUST NOT write an order status history record

#### Scenario: Rejected refund request does not write history
- **WHEN** an `ADMIN` rejects a pending refund request and the order status remains unchanged
- **THEN** the system MUST NOT write an order status history record

#### Scenario: Failed payment callback does not write history
- **WHEN** a mock payment failure callback is processed without changing order status
- **THEN** the system MUST NOT write an order status history record
