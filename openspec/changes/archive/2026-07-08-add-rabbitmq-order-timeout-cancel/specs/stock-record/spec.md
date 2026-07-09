## ADDED Requirements

### Requirement: Timeout Cancellation Release Uses System Operator
The system SHALL write timeout cancellation stock release records with the system timeout audit user as operator.

#### Scenario: Timeout release record has system operator
- **WHEN** timeout cancellation releases locked stock for an order
- **THEN** the system MUST write a `RELEASE` stock record linked to the cancelled order
- **AND** `operator_id` MUST be the user ID of `system_timeout`

#### Scenario: Timeout release record has timeout remark
- **WHEN** timeout cancellation writes a `RELEASE` stock record
- **THEN** the record remark MUST identify that the release came from automatic order timeout cancellation

#### Scenario: Timeout cancellation no-op writes no release record
- **WHEN** timeout cancellation processes an order that is not pending payment
- **THEN** the system MUST NOT write a `RELEASE` stock record
