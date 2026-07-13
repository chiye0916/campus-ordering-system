## Purpose

Payment callback idempotency defines how mock third-party payment callbacks are recorded, validated, retried, and translated into payment/order/stock side effects.

## Requirements

### Requirement: Payment Callback Record Table
The system SHALL store each unique mock third-party payment callback identified by `callbackNo` in a dedicated `payment_callback_record` table separate from the main `payment_record` table.

#### Scenario: Callback table stores callback identity
- **WHEN** a mock payment callback is recorded
- **THEN** the record MUST include callback number, trade number, optional payment record ID, optional order ID, third-party trade number, callback payment status, amount, callback time, process status, optional failure reason, raw payload when available, create time, and update time

#### Scenario: Unknown trade callback can be recorded
- **WHEN** a callback arrives for an unknown trade number
- **THEN** the callback record MUST allow payment record ID and order ID to be null

#### Scenario: Callback number is unique
- **WHEN** the system stores callback records
- **THEN** the database MUST enforce at most one callback record for each `callback_no`

#### Scenario: Repeated delivery does not create another callback row
- **WHEN** the same callback number is delivered more than once
- **THEN** the system MUST reuse the existing callback record for idempotency
- **AND** it MUST NOT insert another callback record for that callback number

### Requirement: Mock Payment Callback Endpoint
The system SHALL expose `POST /payment/mock/callback` for mock third-party payment result callbacks.

#### Scenario: Callback endpoint accepts success request
- **WHEN** a mock provider posts a callback with trade number, callback number, success status, amount, and callback time
- **THEN** the system MUST route the request through `PaymentController` to payment service logic
- **AND** the endpoint MUST return a successful `Result` when callback processing is accepted or idempotently completed

#### Scenario: Callback endpoint validates required fields
- **WHEN** a mock provider posts a callback without trade number, callback number, payment status, amount, or callback time
- **THEN** the system MUST reject the request validation before applying order or stock side effects

#### Scenario: Third-party trade number is optional
- **WHEN** a mock provider posts a callback without third-party trade number
- **THEN** the system MUST accept the request if all required fields are valid
- **AND** it MUST store the third-party trade number when it is provided

#### Scenario: Callback endpoint is mock provider facing
- **WHEN** the mock provider posts to `POST /payment/mock/callback`
- **THEN** the system MUST NOT require a normal user JWT for this mock callback endpoint in this stage

### Requirement: Callback Number Idempotency
The system SHALL use `callbackNo` as the idempotency key for mock payment callback requests.

#### Scenario: First callback number is processed
- **WHEN** a callback arrives with a callback number that has not been recorded
- **THEN** the system MUST create a callback record and process the callback according to its trade number, status, and amount

#### Scenario: Terminal duplicate callback number returns success without side effects
- **WHEN** a callback arrives with a callback number that has already been recorded with terminal process status
- **THEN** the system MUST return success to the mock provider
- **AND** it MUST NOT update the order status again
- **AND** it MUST NOT confirm locked stock again
- **AND** it MUST NOT write duplicate `CONFIRM` stock records

#### Scenario: Processing duplicate callback number is retryable
- **WHEN** a callback arrives with a callback number whose existing callback record is still processing
- **THEN** the system MUST NOT report idempotent provider success for that unfinished processing state
- **AND** it MUST NOT update the order status again
- **AND** it MUST keep the callback retryable

#### Scenario: Stale processing callback can be recovered
- **WHEN** a callback arrives with a callback number whose existing processing callback record is stale
- **THEN** the system MUST allow the callback to be reclaimed or reset for retry
- **AND** it MUST re-run callback validation before reaching a terminal process status

#### Scenario: Recent processing callback stays busy
- **WHEN** a callback arrives with a callback number whose existing processing callback record is recent
- **THEN** the system MUST treat the callback as a retryable in-progress conflict
- **AND** it MUST NOT report provider success

#### Scenario: Concurrent duplicate callback number is idempotent
- **WHEN** two requests concurrently attempt to record the same callback number
- **THEN** only one callback record MUST be created
- **AND** the duplicate-key loser MUST use the existing callback record's process status to decide whether to return terminal idempotent success or retryable in-progress response

#### Scenario: Duplicate callback number with inconsistent payload logs warning
- **WHEN** a repeated callback number is delivered with different trade number, amount, or payment status than the original callback record
- **THEN** the system MUST log a warning
- **AND** it MUST NOT reprocess the changed payload

### Requirement: Callback Amount Validation
The system SHALL validate callback amount against the matching `payment_record.amount` before payment success side effects.

#### Scenario: Amount matches despite scale difference
- **WHEN** callback amount and payment record amount are numerically equal with different decimal scales
- **THEN** the system MUST treat the amounts as matching

#### Scenario: Amount mismatch is recorded without payment success
- **WHEN** callback amount does not match the payment record amount
- **THEN** the system MUST record the callback as failed or abnormal with a failure reason
- **AND** it MUST NOT mark the payment record successful
- **AND** it MUST NOT update the order to paid
- **AND** it MUST NOT confirm locked stock

### Requirement: Callback Trade Validation
The system SHALL match callbacks to payment records by internal mock trade number.

#### Scenario: Unknown trade number is recorded without side effects
- **WHEN** a callback arrives for a trade number that does not match any payment record
- **THEN** the system MUST record the callback with failed process status
- **AND** it MUST NOT update any order
- **AND** it MUST NOT change stock

#### Scenario: Callback for already successful trade is idempotent
- **WHEN** a callback arrives with a new callback number for a trade number whose payment record is already successful
- **THEN** the system MUST record the callback
- **AND** it MUST return success to the mock provider
- **AND** it MUST NOT update the order status again
- **AND** it MUST NOT confirm locked stock again

#### Scenario: New callback number for finalized trade is duplicate business result
- **WHEN** a callback arrives with a new callback number for a trade or order whose successful payment has already been finalized
- **THEN** the callback record MUST use a duplicate or ignored terminal process status to indicate a duplicate business result
- **AND** that duplicate status MUST NOT be used for repeated delivery of the same callback number

### Requirement: Callback Process Status Meanings
The system SHALL use callback process statuses consistently for terminal callback outcomes.

#### Scenario: Duplicate means repeated successful business result
- **WHEN** a new callback number reports success for a trade number whose payment was already successfully finalized
- **THEN** the callback MUST be recorded with duplicate process status

#### Scenario: Ignored means state does not allow requested effect
- **WHEN** a callback is recorded but the order or payment state does not allow the requested effect
- **THEN** the callback MUST be recorded with ignored process status or another clearly documented terminal no-op status

#### Scenario: Failed means validation failed
- **WHEN** callback validation fails because the trade number is unknown or the amount does not match
- **THEN** the callback MUST be recorded with failed process status

#### Scenario: Payment failure callback can be processed
- **WHEN** a `payStatus=FAILED` callback is accepted and applied to the payment record
- **THEN** the callback MUST be allowed to use processed process status
- **AND** the system MUST NOT treat processed process status as meaning payment success

### Requirement: Successful Callback Completes Payment
The system SHALL complete a mock payment only through a successful callback for a valid payment record.

#### Scenario: First successful callback pays pending order
- **WHEN** a successful callback arrives for a valid `PAYING` payment record and the order is still pending payment
- **THEN** the system MUST mark the payment record successful
- **AND** it MUST update the order from pending payment to paid
- **AND** it MUST set the order pay time
- **AND** it MUST confirm locked stock after the order status update succeeds
- **AND** it MUST mark the callback record processed

#### Scenario: Successful callback stores provider trade data
- **WHEN** a successful callback includes a third-party trade number and callback time
- **THEN** the system MUST save those values on the payment record when marking payment successful

### Requirement: Failed Callback Does Not Cancel Order
The system SHALL support mock failed payment callbacks without cancelling the order.

#### Scenario: Failed callback marks payment failed
- **WHEN** a failed callback arrives for a valid `PAYING` payment record
- **THEN** the system MUST record the callback
- **AND** it MUST mark the payment record failed
- **AND** it MUST leave the order pending payment
- **AND** it MUST leave locked stock unchanged

#### Scenario: Failed callback does not release stock
- **WHEN** a failed callback is processed
- **THEN** the system MUST NOT release locked stock
- **AND** it MUST NOT write `RELEASE` stock records

### Requirement: Late Callback After Timeout Is No-Op For Order
The system SHALL record late successful callbacks after timeout cancellation without reviving cancelled orders.

#### Scenario: Successful callback after cancellation is recorded
- **WHEN** a successful callback arrives for an order that has already been cancelled
- **THEN** the system MUST record the callback
- **AND** it MUST NOT update the order to paid
- **AND** it MUST NOT reopen or mark successful a closed payment record
- **AND** it MUST NOT confirm locked stock
- **AND** it MUST return success to the mock provider

#### Scenario: Late callback records reason
- **WHEN** a successful callback cannot pay the order because the order status is no longer pending payment
- **THEN** the callback record MUST include a process status or failure reason identifying that the order status does not allow payment success

### Requirement: Callback Uses Transactional Side Effects
The system SHALL keep first successful callback payment side effects in one transaction.

#### Scenario: Successful callback commits together
- **WHEN** the first successful callback pays a pending order
- **THEN** the payment record update, order paid update, stock confirmation, stock records, and callback record final status MUST commit together

#### Scenario: Stock confirmation failure rolls back payment success
- **WHEN** stock confirmation fails during successful callback processing
- **THEN** the order MUST NOT remain paid
- **AND** the payment record MUST NOT remain successful for that callback
- **AND** the callback MUST NOT be finalized as successfully processed

#### Scenario: Technical failure keeps callback retryable
- **WHEN** a technical failure occurs during callback processing before the callback reaches a terminal process status
- **THEN** the callback handling MUST roll back or otherwise leave the callback retryable
- **AND** the system MUST NOT report terminal idempotent success for the unfinished callback

### Requirement: Callback Response Semantics
The system SHALL distinguish business-final callback outcomes from retryable technical failures when responding to the mock provider.

#### Scenario: Business-final failure returns provider success
- **WHEN** callback processing reaches a terminal business result such as unknown trade number, amount mismatch, terminal payment no-op, late success after cancellation, or duplicate successful business result
- **THEN** the system MUST record a terminal callback process status
- **AND** it MUST return success to the mock provider

#### Scenario: Technical failure remains retryable
- **WHEN** callback processing fails because of Redis lock acquisition failure, database exception, stock confirmation exception, transaction commit failure, or recent unfinished processing conflict
- **THEN** the system MUST NOT finalize the callback as processed, duplicate, failed, or ignored
- **AND** it MUST NOT return provider success
