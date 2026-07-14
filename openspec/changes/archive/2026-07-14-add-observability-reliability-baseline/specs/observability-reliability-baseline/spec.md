## ADDED Requirements

### Requirement: Request TraceId Uses MDC
The system SHALL assign a traceId to every HTTP request and make it available to downstream logs through MDC.

#### Scenario: Existing traceId is propagated
- **WHEN** an HTTP request contains an `X-Trace-Id` header whose value is 8 to 64 characters and contains only letters, digits, hyphen, and underscore
- **THEN** the system MUST use that value as the request traceId
- **AND** logs produced while handling the request MUST be able to include that traceId
- **AND** the HTTP response MUST include the same `X-Trace-Id` value

#### Scenario: Invalid traceId is replaced
- **WHEN** an HTTP request contains a blank, too short, too long, control-character, newline, or otherwise unsafe `X-Trace-Id` header
- **THEN** the system MUST ignore the supplied value
- **AND** it MUST generate a new traceId
- **AND** logs produced while handling the request MUST use the generated traceId
- **AND** the HTTP response MUST include the generated `X-Trace-Id` value

#### Scenario: Missing traceId is generated
- **WHEN** an HTTP request does not contain a non-blank `X-Trace-Id` header
- **THEN** the system MUST generate a traceId
- **AND** logs produced while handling the request MUST be able to include that traceId
- **AND** the HTTP response MUST include the generated `X-Trace-Id` value

#### Scenario: TraceId appears in log output
- **WHEN** application code logs while traceId is present in MDC
- **THEN** the configured application log pattern MUST include the MDC traceId value in emitted logs

#### Scenario: MDC is cleared after request
- **WHEN** request handling completes successfully or with an exception
- **THEN** the system MUST remove request trace data from MDC before the thread is reused

### Requirement: Non-HTTP Work Has Trace Context
The system SHALL provide trace context for scheduled jobs and RabbitMQ message handling even when no HTTP request exists.

#### Scenario: Outbox stores originating traceId
- **WHEN** an HTTP order submit request creates a timeout outbox record
- **THEN** the system MUST store the current traceId in a nullable `trace_id` column on `order_timeout_outbox`
- **AND** missing traceId data MUST NOT prevent outbox record creation

#### Scenario: Outbox publisher propagates traceId
- **WHEN** the timeout outbox publisher publishes a record that has `trace_id`
- **THEN** the publisher MUST use that traceId for its publish logs
- **AND** it MUST propagate the traceId to RabbitMQ message metadata when practical

#### Scenario: Scheduled outbox publish has traceId
- **WHEN** the timeout outbox publisher runs from a scheduled job
- **THEN** publish attempt, confirm, retry, stale recovery, and failure logs MUST include a traceId
- **AND** those logs MUST include the relevant `messageId` and `orderId` when available

#### Scenario: Timeout cancel listener has traceId
- **WHEN** the timeout cancel RabbitMQ listener handles an order timeout message
- **THEN** listener logs MUST use the message traceId when available
- **AND** listener logs MUST generate a new traceId when no message traceId is available
- **AND** those logs MUST include `messageId` and `orderId` when available

### Requirement: Business Logs Include Correlation Fields
The system SHALL log key business outcomes with traceId and relevant business identifiers.

#### Scenario: Order submit logs outcome
- **WHEN** an order submit attempt succeeds or fails
- **THEN** the system MUST log the outcome with traceId
- **AND** it MUST include `userId`, `orderId`, and `idempotencyKey` when available
- **AND** it MUST include a concise result or failure reason

#### Scenario: Payment initiation logs outcome
- **WHEN** mock payment initiation succeeds or fails
- **THEN** the system MUST log the outcome with traceId
- **AND** it MUST include `userId`, `orderId`, and `tradeNo` when available

#### Scenario: Payment callback logs outcome
- **WHEN** a mock payment callback is processed
- **THEN** the system MUST log the outcome with traceId
- **AND** it MUST include `tradeNo`, `callbackNo`, `orderId`, and `paymentRecordId` when available
- **AND** it MUST distinguish success, duplicate, amount mismatch, failed payment, ignored, and retryable technical conflict outcomes when applicable

#### Scenario: Order cancel logs outcome
- **WHEN** an order is manually cancelled or automatically cancelled by timeout
- **THEN** the system MUST log the outcome with traceId
- **AND** it MUST include `userId` or system operator, `orderId`, and cancel source when available

#### Scenario: Redis cache recovery logs outcome
- **WHEN** dish-list cache read, write, delete, or corrupted-cache recovery fails
- **THEN** the system MUST log the cache operation with traceId
- **AND** it MUST include `categoryId`, cache key, operation, and failure summary when available

### Requirement: Global Exception Logs Are Diagnostic
The system SHALL log handled exceptions with enough request context to diagnose failures without changing the existing `Result<T>` response contract.

#### Scenario: Business exception is logged with request context
- **WHEN** a `BusinessException` is handled by the global exception handler
- **THEN** the system MUST log traceId, path, method, errorCode, message, and exceptionClass
- **AND** it MUST preserve the existing business error response shape

#### Scenario: Unexpected exception is logged with request context
- **WHEN** an unexpected exception is handled by the global exception handler
- **THEN** the system MUST log traceId, path, method, errorCode, message, and exceptionClass
- **AND** it MUST preserve the existing internal-error response behavior

### Requirement: Actuator Exposure Is Minimal
The system SHALL expose only safe Actuator endpoints for this stage.

#### Scenario: Only selected actuator endpoints are exposed
- **WHEN** the application starts with Actuator enabled
- **THEN** the web exposure list MUST include only `health`, `info`, and `metrics`
- **AND** it MUST NOT expose `env`, `beans`, `configprops`, `heapdump`, `threaddump`, or `prometheus`

#### Scenario: Health details are not exposed
- **WHEN** a caller reads the Actuator health endpoint without management endpoint authentication
- **THEN** the health response MUST NOT expose detailed MySQL, Redis, RabbitMQ, or environment internals

#### Scenario: Prometheus endpoint is not exposed
- **WHEN** the project does not include a Prometheus registry dependency
- **THEN** the system MUST NOT expose `/actuator/prometheus`

#### Scenario: Info endpoint is non-sensitive
- **WHEN** a caller reads the Actuator info endpoint
- **THEN** the response MUST NOT expose database addresses, Redis addresses, RabbitMQ addresses, JWT secrets, mail credentials, payment secrets, or other operational secrets
- **AND** it MAY expose only non-sensitive application metadata such as app name, version, or build time
- **AND** an empty or minimal info response MUST be acceptable in this stage

### Requirement: Metrics Use Low-Cardinality Tags
The system SHALL record custom Micrometer metrics using low-cardinality tags only.

#### Scenario: High-cardinality identifiers are excluded from metric tags
- **WHEN** the system records any custom business metric
- **THEN** metric tags MUST NOT include `userId`, `orderId`, `tradeNo`, `callbackNo`, `messageId`, JWT token, or request payload values
- **AND** those high-cardinality identifiers MAY appear in logs when available

#### Scenario: Metrics use stable low-cardinality tags
- **WHEN** the system records custom business metrics
- **THEN** metric tags MUST be limited to stable low-cardinality values such as `result`, `reason`, `status`, and `operation`

### Requirement: Key Business Metrics Are Recorded
The system SHALL expose custom metrics for cache, order, payment, timeout cancel, and outbox visibility through `/actuator/metrics`.

#### Scenario: Metrics failures do not affect business results
- **WHEN** custom metrics recording fails during a business operation
- **THEN** the metrics failure MUST NOT change the API response
- **AND** it MUST NOT swallow an existing business exception
- **AND** it MUST NOT turn a successful business operation into a failure

#### Scenario: Dish-list cache metric is recorded
- **WHEN** `/dish/list` uses the dish-list cache path
- **THEN** the system MUST record a `dish.list.cache.requests` metric
- **AND** it MUST tag the metric with a low-cardinality `result` such as `hit`, `miss`, or `error`

#### Scenario: Order submit metric is recorded
- **WHEN** an order submit attempt succeeds or fails
- **THEN** the system MUST record an `order.submit.requests` metric
- **AND** it MUST tag the metric with low-cardinality `result` and `reason` values

#### Scenario: Payment callback metric is recorded
- **WHEN** a mock payment callback is processed
- **THEN** the system MUST record a `payment.callback.requests` metric
- **AND** it MUST tag the metric with a low-cardinality result such as `success`, `duplicate`, `amount_mismatch`, `failed`, or `ignored`

#### Scenario: Timeout cancel metric is recorded
- **WHEN** the timeout cancel flow handles a timeout message
- **THEN** the system MUST record an `order.timeout.cancel.requests` metric
- **AND** it MUST tag the metric with a low-cardinality result such as `success`, `noop`, or `fail`

#### Scenario: Outbox status gauges are exposed
- **WHEN** `/actuator/metrics` is queried for timeout outbox visibility
- **THEN** the system MUST expose an `order.timeout.outbox.records` gauge
- **AND** it MUST tag counts by low-cardinality outbox status such as `pending`, `publishing`, `sent`, or `failed`

### Requirement: RabbitMQ And Outbox Reliability Is Visible
The system SHALL log and measure timeout outbox publisher and RabbitMQ reliability outcomes.

#### Scenario: Publisher confirm failure is visible
- **WHEN** RabbitMQ publisher confirm reports failure or no acknowledgement
- **THEN** the system MUST log the failure with traceId, messageId, orderId, and failure summary when available
- **AND** it MUST record a low-cardinality outbox failure metric or status count

#### Scenario: Stale publishing recovery is visible
- **WHEN** stale `PUBLISHING` timeout outbox records are recovered for retry
- **THEN** the system MUST log the recovery with traceId and recovered count
- **AND** it MUST expose the resulting outbox status counts through metrics

#### Scenario: Max retry failure is visible
- **WHEN** a timeout outbox record reaches max retry failure
- **THEN** the system MUST log the failure with traceId, messageId, orderId, retry count, and last error summary when available
- **AND** it MUST be visible in the failed outbox metrics count

### Requirement: Manual K6 Smoke Scripts Are Provided
The system SHALL provide manual k6 smoke/load scripts and documentation for selected business APIs without adding them to automated validation.

#### Scenario: Dish-list k6 script is documented
- **WHEN** a developer wants to manually smoke test `/dish/list`
- **THEN** the project MUST provide a k6 script and documentation for running it against a local application
- **AND** the script MUST accept environment-specific values such as base URL and category ID through configuration or environment variables

#### Scenario: Order-submit k6 script is documented
- **WHEN** a developer wants to manually smoke test `/order/submit`
- **THEN** the project MUST provide a k6 script and documentation for running it against a local application
- **AND** the script MUST make clear that authentication, cart data, stock data, and idempotency keys are required test inputs

#### Scenario: Payment-callback k6 script is documented
- **WHEN** a developer wants to manually smoke test `/payment/mock/callback`
- **THEN** the project MUST provide a k6 script and documentation for running it against a local application
- **AND** the script MUST make clear that valid payment trade data is required

#### Scenario: K6 is not required by Maven or CI
- **WHEN** a developer runs `./mvnw test` or `./mvnw verify -Pintegration-test`
- **THEN** k6 scripts MUST NOT be required to run
- **AND** k6 results MUST NOT be strict pass/fail gates in this stage
