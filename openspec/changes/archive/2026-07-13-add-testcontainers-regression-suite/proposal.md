## Why

The project now contains several transaction-sensitive flows around dish cache, order submit idempotency, stock locking, payment callback idempotency, and Redis/RabbitMQ integration. Existing Mockito unit tests are valuable, but they cannot prove that MyBatis XML, real MySQL transactions, Redis serialization/TTL, Spring MVC interceptors, and container-backed configuration work together.

## What Changes

- Add a Testcontainers-based integration regression suite that runs separately from the fast unit-test workflow.
- Add Maven integration-test wiring so:
  - `./mvnw test` continues to run fast unit tests and does not require Docker.
  - `./mvnw verify -Pintegration-test` runs `*IT` Testcontainers integration tests and requires Docker.
- Add a shared integration-test base that starts real MySQL and Redis containers, injects dynamic Spring properties, initializes schema from `sql/schema.sql`, and provides explicit database/Redis cleanup helpers.
- Add RabbitMQ container support only as needed for application context and existing messaging configuration; full RabbitMQ TTL/DLX timeout-cancel behavior is out of scope for this stage.
- Add lightweight test-data helpers for users, login/JWT token extraction, categories, dishes, stock, carts, orders, and payment setup.
- Cover the first three high-value real-container regression flows:
  - dish list cache with real Redis
  - order submit idempotency plus stock locking with real MySQL
  - payment callback idempotency plus stock confirmation with real MySQL and Redis
- Keep integration tests independent of local pre-existing MySQL/Redis/RabbitMQ data and independent of test execution order.
- Include pre-change documentation/spec cleanup discovered after stage six:
  - fix the stale active-change note in `docs/PROJECT_CONTEXT.md`
  - replace the archived `dish-list-cache` spec Purpose `TBD` text with a real purpose
- Out of scope: load testing, monitoring/logging platform work, frontend E2E tests, real payment-provider integration, broad historical endpoint coverage, full RabbitMQ delayed timeout cancellation regression, concurrency stress tests, and large production-code refactors solely for testing.

## Capabilities

### New Capabilities

- `testcontainers-regression-suite`: Defines the project integration-test workflow, container-backed infrastructure, data cleanup rules, and the first three critical regression flows.

### Modified Capabilities

- None.

## Impact

- Affected build: add test-scoped Testcontainers dependencies and a Maven integration-test profile/Failsafe configuration for `*IT` tests.
- Affected tests: add integration-test base classes, cleanup utilities, test-data helpers, and `*IT` regression tests.
- Affected infrastructure: integration tests require Docker only when running the integration-test profile.
- Affected docs/specs: update test-running documentation, project context, and the `dish-list-cache` spec Purpose cleanup.
