## 1. Dependency And Configuration

- [x] 1.1 Add `spring-boot-starter-actuator` to `pom.xml` without adding `micrometer-registry-prometheus`.
- [x] 1.2 Configure Actuator to expose only `health`, `info`, and `metrics`.
- [x] 1.3 Configure health detail exposure conservatively with `management.endpoint.health.show-details=never`.
- [x] 1.4 Verify sensitive Actuator endpoints such as `env`, `beans`, `configprops`, `heapdump`, `threaddump`, and `prometheus` are not exposed.
- [x] 1.5 Ensure `/actuator/info` exposes only non-sensitive metadata such as app name, version, or build time, or remains minimal/empty.

## 2. TraceId And MDC

- [x] 2.1 Add a request trace component that accepts `X-Trace-Id` only when it is 8 to 64 characters and contains only letters, digits, hyphen, or underscore; generate a new traceId otherwise.
- [x] 2.2 Put traceId into MDC for the duration of each HTTP request and return it in the `X-Trace-Id` response header.
- [x] 2.3 Clear traceId from MDC in a `finally` path after every request.
- [x] 2.4 Add logging pattern support so `%X{traceId}` from MDC appears in application console logs.
- [x] 2.5 Add trace context helpers for scheduled jobs and RabbitMQ listener work that do not start from HTTP.
- [x] 2.6 Add focused tests proving valid traceId propagation, invalid traceId replacement, generation, response header behavior, and MDC cleanup.

## 3. Diagnostic Logging

- [x] 3.1 Enhance `GlobalExceptionHandler` logs with traceId, path, method, errorCode, message, and exceptionClass while preserving existing `Result<T>` responses.
- [x] 3.2 Add standardized order submit logs for success, idempotent duplicate, conflict, stock failure, and unexpected failure outcomes.
- [x] 3.3 Add standardized mock payment initiation logs with userId, orderId, and tradeNo when available.
- [x] 3.4 Add standardized payment callback logs for success, duplicate, amount mismatch, failed payment, ignored, and retryable conflict outcomes.
- [x] 3.5 Add standardized manual cancel and timeout cancel logs with orderId, user/system operator, source, and result.
- [x] 3.6 Add outbox publisher logs for claim, publish attempt, confirm success, confirm failure, retry, stale publishing recovery, and max retry failure.
- [x] 3.7 Review Redis dish-list cache logs so read/write/delete failures and corrupted-cache recovery include traceId, categoryId, key, operation, and failure summary without logging secrets.

## 4. Micrometer Metrics

- [x] 4.1 Add a small metrics component or helper methods for recording custom counters/timers/gauges with low-cardinality tags only.
- [x] 4.2 Add `dish.list.cache.requests` metrics tagged by `result=hit|miss|error`.
- [x] 4.3 Add `order.submit.requests` metrics tagged by low-cardinality `result` and `reason`.
- [x] 4.4 Add `payment.callback.requests` metrics tagged by low-cardinality callback result.
- [x] 4.5 Add `order.timeout.cancel.requests` metrics tagged by `result=success|noop|fail`.
- [x] 4.6 Add `order.timeout.outbox.records` gauges grouped by outbox status `pending|publishing|sent|failed`.
- [x] 4.7 Ensure metrics recording failures do not change API responses, swallow business exceptions, or turn successful business operations into failures.
- [x] 4.8 Add tests or lightweight assertions ensuring custom metric tags do not include userId, orderId, tradeNo, callbackNo, messageId, tokens, or payload values.

## 5. Outbox And RabbitMQ Visibility

- [x] 5.1 Add nullable `trace_id` support to `order_timeout_outbox` schema, entity, mapper, and test schema initialization.
- [x] 5.2 Store the current traceId when order submit creates a timeout outbox record.
- [x] 5.3 Propagate existing traceId through outbox publisher work and RabbitMQ message headers when available; generate a new traceId at scheduler/listener boundaries when absent.
- [x] 5.4 Add or reuse mapper/query support for counting timeout outbox records by status for gauges.
- [x] 5.5 Ensure publisher confirm failure and no-ack paths are logged and reflected in outbox status visibility.
- [x] 5.6 Ensure stale publishing recovery logs recovered counts and leaves status counts visible through metrics.
- [x] 5.7 Ensure max retry failures are logged with messageId/orderId/retry count when available and visible in failed outbox counts.

## 6. K6 Manual Smoke Scripts

- [x] 6.1 Add a manual k6 script for `/dish/list` that accepts base URL and category ID inputs.
- [x] 6.2 Add a manual k6 script for `/order/submit` that documents required token, cart data, stock data, and idempotency key inputs.
- [x] 6.3 Add a manual k6 script for `/payment/mock/callback` that documents required valid trade/callback data inputs.
- [x] 6.4 Document k6 usage and clarify that scripts are manual smoke/load helpers, not Maven or CI gates.

## 7. Documentation And Verification

- [x] 7.1 Update project/API testing documentation with Actuator endpoint scope, metrics viewing examples, traceId usage, and k6 manual commands.
- [x] 7.2 Run `openspec validate add-observability-reliability-baseline --strict`.
- [x] 7.3 Run `openspec validate --all --strict`.
- [x] 7.4 Run `./mvnw test` and confirm default tests do not require Docker.
- [x] 7.5 Run `./mvnw verify -Pintegration-test` when Docker/Testcontainers access is available, or document the environment reason if it cannot run. Result: passed with Docker/Testcontainers access; 9 integration tests completed with 0 failures and 0 errors.
- [x] 7.6 Manually verify `/actuator/health`, `/actuator/info`, and `/actuator/metrics` are available and sensitive endpoints are not exposed.
  - Result: user manually verified `health`, `info`, and `metrics` return successful Actuator responses with `X-Trace-Id`; sensitive endpoints return the app's not-found response body and are not exposed.
