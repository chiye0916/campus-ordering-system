## ADDED Requirements

### Requirement: Lock-Rejected Payment Does Not Create Successful Record
The system SHALL NOT leave a successful payment record when a payment request is rejected before entering the payment flow because the order status lock cannot be acquired.

#### Scenario: Payment lock acquisition fails
- **WHEN** a payment request cannot acquire `lock:order:status:{orderId}`
- **THEN** the system MUST reject the payment and MUST NOT create a successful payment record
