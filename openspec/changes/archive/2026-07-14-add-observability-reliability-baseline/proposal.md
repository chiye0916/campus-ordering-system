## Why

The order, payment, cache, and timeout-outbox flows now have enough moving parts that failures need to be traceable across HTTP requests, service logs, Redis, RabbitMQ, and database side effects. This change establishes a lightweight observability and reliability baseline so the system is easier to inspect and troubleshoot without building a full monitoring platform.

## What Changes

- Add request traceId support with safe `X-Trace-Id` validation and MDC so HTTP, service, MQ, and exception logs can be correlated.
- Add logging pattern support so MDC traceId values are actually emitted in application logs.
- Standardize key business logs for order submit, mock payment initiation, payment callbacks, order cancel and timeout cancel, timeout outbox publishing, RabbitMQ confirm/failure paths, and Redis cache error recovery.
- Enhance global exception logging with traceId, path, method, error code, message, exception class, and available business identifiers.
- Add Spring Boot Actuator with only `health`, `info`, and `metrics` web endpoints exposed.
- Configure health details conservatively so unauthenticated management endpoints do not expose infrastructure details.
- Add Micrometer counters, timers, and gauges for key business flows and outbox visibility using only low-cardinality tags.
- Propagate traceId through timeout outbox and RabbitMQ work when available, including a nullable `trace_id` on `order_timeout_outbox`.
- Add manual k6 smoke/load scripts and documentation for `/dish/list`, `/order/submit`, and `/payment/mock/callback`.
- Do not change core order, payment, stock, cache, or timeout business semantics.

## Capabilities

### New Capabilities

- `observability-reliability-baseline`: Defines traceId/MDC behavior, structured business logging, safe Actuator exposure, Micrometer metric rules, outbox/RabbitMQ visibility, and manual k6 smoke/load checks.

### Modified Capabilities

- None. Existing business capabilities keep their current requirements; this change adds cross-cutting observability around them.

## Impact

- Affected code: web interceptor/filter configuration, global exception handling, order/payment/cache/outbox/timeout service logging, metrics instrumentation, and application configuration.
- Affected database/schema: add a nullable `trace_id` column to `order_timeout_outbox` so asynchronous publisher and listener work can continue the originating request trace when available.
- Affected dependencies: add `spring-boot-starter-actuator`; do not add `micrometer-registry-prometheus` in this stage.
- Affected APIs: new Actuator management endpoints `/actuator/health`, `/actuator/info`, and `/actuator/metrics`; existing business API response shapes remain unchanged.
- Affected docs/scripts: add manual k6 scripts and documentation for smoke/load checks; k6 is not part of Maven, CI, or required validation.
