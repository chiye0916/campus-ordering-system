## Context

The system now includes Redis-backed dish-list caching, transactional order submission with idempotency, stock locking and confirmation, mock payment callbacks, RabbitMQ timeout cancellation, and timeout outbox publishing. These flows are correct but harder to diagnose when a request crosses HTTP, service logic, Redis, MySQL, RabbitMQ, and scheduled jobs.

This design adds a small observability layer without changing business semantics. It keeps the current architecture: Spring MVC controllers, services, MyBatis XML mappers, JWT + `HandlerInterceptor`, `BaseContext`, and `Result<T>` responses.

## Goals / Non-Goals

**Goals:**

- Correlate HTTP, service, MQ, scheduler, cache, and exception logs with a traceId.
- Add standard business log fields for the most important flows: order submit, payment initiation, payment callback, manual cancel, timeout cancel, outbox publish/confirm/failure, and Redis cache recovery.
- Expose safe Actuator endpoints: `health`, `info`, and `metrics`.
- Add Micrometer counters, timers, and gauges with low-cardinality tags only.
- Add manual k6 smoke/load scripts and documentation for learning and local troubleshooting.
- Preserve existing API response shapes and business semantics.

**Non-Goals:**

- Do not introduce Spring Security.
- Do not add Prometheus registry or expose `/actuator/prometheus`.
- Do not expose sensitive Actuator endpoints such as `env`, `beans`, `configprops`, `heapdump`, or `threaddump`.
- Do not add Zipkin, Jaeger, OpenTelemetry tracing, or a full monitoring platform.
- Do not use high-cardinality values such as `userId`, `orderId`, `tradeNo`, `callbackNo`, or `messageId` as metrics tags.
- Do not make k6 part of Maven, CI, or strict validation.
- Do not change order, payment, stock, cache, or timeout business rules.

## Decisions

1. Use an HTTP filter or interceptor for traceId/MDC with safe header validation.

   The request entry point will read `X-Trace-Id` only when it matches a safe pattern: length 8 to 64, characters limited to letters, digits, hyphen, and underscore. Invalid, blank, overlong, or control-character values are ignored and replaced with a generated traceId. The component will put `traceId` into MDC before downstream handling and remove it in a `finally` block. The response will include the accepted or generated `X-Trace-Id` so API callers can correlate their request with logs.

   Alternative considered: put traceId logic in every controller. That would duplicate code and risk missing paths such as exceptions.

2. Use MDC and structured log messages, not a new logging framework.

   Existing SLF4J logging can carry traceId and business identifiers. Application logging configuration must include `%X{traceId}` in the console pattern so the MDC value is visible in actual logs. Business IDs such as `userId`, `orderId`, `tradeNo`, `callbackNo`, and `messageId` should appear in logs where available. This gives useful search fields without introducing JSON log infrastructure in this stage.

   Alternative considered: switch the whole project to JSON logs. That is useful later, but too broad for the current learning/demo stage.

3. Keep high-cardinality fields out of metrics tags.

   Metrics will use low-cardinality tags such as `result`, `reason`, `status`, and `operation`. High-cardinality fields belong in logs. This avoids unbounded time series growth if metrics are later scraped by a monitoring system.

   Alternative considered: tag metrics with order or trade identifiers for easier lookup. That makes metrics expensive and fragile; logs are the right place for those IDs.

4. Use Spring Boot Actuator's built-in Micrometer support only.

   Add `spring-boot-starter-actuator`, inject `MeterRegistry`, and register custom counters/timers/gauges through the default registry. Expose `/actuator/metrics` for local inspection. Do not add `micrometer-registry-prometheus`, because this stage does not include Prometheus/Grafana.

   Alternative considered: add Prometheus registry immediately. That would imply a collection/export story this change explicitly excludes.

5. Use conservative Actuator exposure.

   Configure:

   ```properties
   management.endpoints.web.exposure.include=health,info,metrics
   management.endpoint.health.show-details=never
   ```

   This keeps unauthenticated Actuator endpoints from exposing database, Redis, RabbitMQ, environment, heap, or bean details.

   The `info` endpoint may expose only non-sensitive application metadata such as app name, version, and build time. It must not expose database addresses, Redis addresses, RabbitMQ addresses, JWT secrets, mail credentials, payment secrets, or other operational secrets. An empty or minimal `info` response is acceptable in this stage.

6. Instrument flows at service boundaries.

   Counters and timers should be added where outcomes are known: dish cache get/list paths, order submit success/failure paths, payment initiation, payment callback result classification, timeout cancel listener results, and outbox publisher state transitions. Controller-to-Service-to-Mapper behavior remains the same; instrumentation observes results after service decisions instead of replacing business control flow. Metrics recording must be best-effort: a metrics failure must not change API responses, swallow business exceptions, or turn a successful business operation into a failure.

7. Propagate traceId through timeout outbox and RabbitMQ boundaries.

   When an HTTP request creates a timeout outbox record, the current traceId should be stored in a nullable `trace_id` column on `order_timeout_outbox`. The publisher should restore that traceId into MDC while publishing that record and add it to RabbitMQ message headers where practical. The listener should use the message traceId when available. Scheduler or listener work without an existing traceId should generate a new one at the boundary.

   Alternative considered: avoid schema changes and generate fresh traceIds for all asynchronous work. That keeps the database unchanged but loses the ability to connect the original order submit request to later outbox publish logs.

8. Make outbox state visible through gauges.

   A small metrics component can query `order_timeout_outbox` counts grouped by status and expose gauges tagged with `status=pending|publishing|sent|failed`. This uses the existing table as the source of truth and does not add a new table or background page.

9. Keep k6 scripts manual and environment-driven.

   Scripts should live under `scripts/k6/` or another documented scripts folder and accept base URL, tokens, IDs, and callback values through environment variables. They are for manual smoke/load checks against a running local app, not CI gates.

## Risks / Trade-offs

- [Risk] Logging sensitive payload data while adding richer logs. -> Mitigation: log IDs, status, reason, and summary fields; do not log passwords, JWT tokens, full callback payloads, or infrastructure secrets.
- [Risk] Metrics instrumentation changes exception behavior accidentally. -> Mitigation: metrics recording must be best-effort and must not swallow or replace existing business exceptions.
- [Risk] User-supplied traceId pollutes logs. -> Mitigation: accept only safe `X-Trace-Id` values with bounded length and characters; generate a new traceId otherwise.
- [Risk] Outbox gauges query the database too frequently. -> Mitigation: keep gauges simple, reuse existing mapper/JdbcTemplate patterns, and rely on Actuator metric scrape/read frequency instead of adding a custom scheduler.
- [Risk] TraceId leaks between requests through thread reuse. -> Mitigation: always clear MDC in `finally`, matching the existing `BaseContext` cleanup principle.
- [Risk] MQ or scheduled jobs do not naturally have HTTP traceIds. -> Mitigation: propagate existing traceId through outbox/message metadata when available; generate one at scheduler/listener boundaries when absent.
- [Risk] Actuator endpoints expose too much. -> Mitigation: explicitly include only `health`, `info`, and `metrics`; set health details to `never`; do not expose `prometheus` without a later registry change.
