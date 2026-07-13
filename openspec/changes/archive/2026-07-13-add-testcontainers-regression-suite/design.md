## Context

The first six stages added meaningful business complexity: order state transitions, order submit idempotency, stock locking/confirmation/release, RabbitMQ timeout outbox, payment callback idempotency, and enhanced Redis dish-list caching. The current test suite is mostly Mockito/unit-test focused and runs quickly through `./mvnw test`.

That unit-test layer should remain fast, but it does not verify real integration points:

```text
MockMvc / Spring MVC / Interceptor / JWT
  -> Controller
  -> Service transaction
  -> MyBatis Mapper XML
  -> real MySQL constraints and rows
  -> real Redis cache/TTL/locks
  -> optional RabbitMQ application context wiring
```

This change adds a focused Testcontainers regression suite to lock down the most valuable flows without trying to cover every historical endpoint.

## Goals / Non-Goals

**Goals:**

- Add Testcontainers infrastructure for real MySQL and Redis integration tests.
- Add RabbitMQ Testcontainers support only when needed for application startup or lightweight messaging configuration checks.
- Keep `./mvnw test` as the fast unit-test workflow and keep it free of Docker/Testcontainers requirements.
- Add `./mvnw verify -Pintegration-test` as the explicit integration regression command.
- Use Maven Failsafe for `*IT` classes so integration tests are isolated from Surefire `*Test` classes.
- Initialize schema from `sql/schema.sql` and provide explicit database/Redis cleanup before each integration test.
- Add small test-data helpers in test source only.
- Cover three first-pass regression flows: dish-list cache, order submit idempotency plus stock locking, and payment callback idempotency plus stock confirmation.
- Document Docker requirements and test commands.
- Clean up stale stage-six context/spec documentation while creating this stage.

**Non-Goals:**

- Do not make default `./mvnw test` require Docker.
- Do not depend on locally running MySQL, Redis, RabbitMQ, or pre-existing local data.
- Do not depend on integration test execution order.
- Do not use `@Transactional` rollback as the only cleanup mechanism.
- Do not add load tests, performance assertions, frontend E2E tests, broad historical API coverage, real payment-provider integration, or large production-code refactors solely for tests.
- Do not fully test RabbitMQ TTL/DLX timeout cancellation behavior in this stage.
- Do not wait for real RabbitMQ delayed-message expiration in this stage.
- Do not add fragile concurrency stress tests in the first Testcontainers stage.

## Decisions

### Decision: Separate Unit Tests And Integration Tests

`./mvnw test` should continue to run fast `*Test` classes through Maven Surefire. Testcontainers tests should be named `*IT` and run through Maven Failsafe only when the `integration-test` profile is enabled:

```text
./mvnw test
  Surefire
  *Test
  no Docker requirement

./mvnw verify -Pintegration-test
  Surefire + Failsafe
  *Test + *IT
  Docker required for Testcontainers
```

This preserves the day-to-day developer loop while still providing a stronger regression command before larger changes.

Surefire should explicitly include normal `*Test` classes and exclude `*IT` classes. Failsafe should explicitly include `*IT` classes inside the `integration-test` profile. This prevents future Maven changes from accidentally starting Testcontainers during the default unit-test command.

### Decision: Use GenericContainer For Redis

Use standard Testcontainers modules for MySQL and RabbitMQ where practical. Redis should use `GenericContainer`, for example `redis:7-alpine` with port `6379`, because there is no project-standard Redis module equivalent to MySQL/RabbitMQ in the current dependency plan.

### Decision: Use Shared BaseIntegrationTest

Add a test-only base class, such as `BaseIntegrationTest`, that owns:

- MySQL container lifecycle
- Redis container lifecycle
- optional RabbitMQ container lifecycle
- `@DynamicPropertySource` property injection
- `@SpringBootTest`
- `@AutoConfigureMockMvc`
- schema initialization
- database cleanup helper
- Redis cleanup helper
- common JSON/ObjectMapper/MockMvc helpers

The base class should inject dynamic properties for:

- `spring.datasource.*`
- `spring.data.redis.*`
- `spring.rabbitmq.*` when RabbitMQ is enabled
- test-friendly TTL or timeout values when useful
- `spring.rabbitmq.listener.simple.auto-startup=false` unless a test explicitly needs listeners

### Decision: Initialize Schema Once And Clean Explicitly

The MySQL container should be initialized from `sql/schema.sql` or an equivalent schema initializer. Schema initialization must run exactly once for the integration-test database. Do not configure both a Testcontainers init script and Spring SQL initialization to execute the same schema at startup.

Each test method should then clean data explicitly and insert only its own required test data.

Cleanup should not rely on test execution order or local data. It should also not rely solely on `@Transactional` rollback because these tests use MockMvc HTTP requests, Redis, optional messaging, and committed transaction checks. Prefer explicit cleanup:

```text
database cleanup:
  delete business rows in dependency order or truncate/disable checks safely
  examples of dependent tables include payment_callback_record,
  payment_record, stock_record, order_detail, orders,
  order_timeout_outbox, shopping_cart, dish_stock, dish,
  category, and user

redis cleanup:
  flush test database or delete keys created by the tests

rabbit cleanup:
  purge only queues used by integration tests when needed
```

Assertions should avoid brittle auto-increment ID assumptions. Tests should capture IDs from responses or queries.

### Decision: Keep Test Data Helpers Small

Add test-only helpers for common setup:

- create user/admin or insert users directly when the test does not focus on registration
- login and extract JWT token through MockMvc when the test needs authentication behavior
- create categories and dishes
- initialize dish stock
- add cart items
- query database state for assertions

These helpers should support the first three flows without becoming a large testing framework.

### Decision: Cover Three High-Value Flows First

The first Testcontainers suite should include:

1. Dish cache integration with real Redis:
   - cache miss writes Redis
   - cache hit returns cached data without querying the dish-list SQL path where observable; category existence validation may still query `CategoryMapper`
   - empty list caches JSON `[]`
   - mutation invalidates cache
   - TTL is set without waiting for real expiry

2. Order submit integration with real MySQL:
   - login or authenticated request path works
   - cart submit creates order and details
   - stock available/locked quantities update correctly
   - duplicate `Idempotency-Key` returns the original order and does not relock stock
   - insufficient stock fails without creating an order

3. Payment callback integration with real MySQL and Redis:
   - payment initiation returns `PAYING` trade data
   - successful callback marks order paid and confirms locked stock
   - repeated delivery of the same `callbackNo` does not duplicate stock confirmation
   - amount mismatch does not pay the order
   - failed callback leaves the order pending payment and stock locked, following the current payment rule that failed payment does not cancel the order or release stock

RabbitMQ should remain lightweight in this stage. Start a RabbitMQ container only if application context wiring or a specific integration test needs real broker properties. Timeout outbox assertions are optional in this stage and should be included only if they fit naturally into the first suite. Full TTL expiration, dead-letter routing, listener retry, delayed-message waiting, and timeout cancellation release behavior stay out of scope.

### Decision: Test TTL Without Sleeping For Expiry

Dish cache tests should not sleep for 30 minutes or 5 minutes. They should verify that the Redis key exists and has a positive TTL in a reasonable range. Test properties may shorten TTL values, but tests should still avoid relying on real expiration timing.

## Risks / Trade-offs

- [Risk] Testcontainers makes integration tests slower and Docker-dependent. -> Mitigation: keep them behind `./mvnw verify -Pintegration-test`; keep `./mvnw test` fast and Docker-free.
- [Risk] Container startup and Spring context setup can make tests flaky if data leaks between methods. -> Mitigation: explicit DB/Redis cleanup before each method and no test-order assumptions.
- [Risk] Full RabbitMQ timeout behavior can dominate the stage. -> Mitigation: limit RabbitMQ to lightweight startup/outbox checks and leave TTL/DLX listener regression to a later reliability stage.
- [Risk] Integration helpers can grow into a hidden framework. -> Mitigation: keep helpers focused on the three first-pass flows and test source only.
- [Risk] Schema initialization may drift from production schema. -> Mitigation: reuse `sql/schema.sql` and keep schema assertions close to real mapper/service behavior.
