## 1. Pre-Change Cleanup

- [x] 1.1 Update `docs/PROJECT_CONTEXT.md` so it no longer says `enhance-redis-dish-cache` is the active change.
- [x] 1.2 Update `openspec/specs/dish-list-cache/spec.md` Purpose from the archived `TBD` placeholder to a meaningful dish-list cache purpose.
- [x] 1.3 Run `openspec validate --all --strict` after the cleanup and fix validation issues.

## 2. Maven And Testcontainers Setup

- [x] 2.1 Add test-scoped Testcontainers dependencies for JUnit Jupiter, MySQL, RabbitMQ where needed, and use `GenericContainer` with a Redis image such as `redis:7-alpine` for Redis.
- [x] 2.2 Configure Maven Surefire so default `./mvnw test` runs normal `*Test` unit tests, explicitly excludes `*IT`, and does not require Docker.
- [x] 2.3 Add an `integration-test` Maven profile that configures Failsafe to include and run `*IT` classes during `./mvnw verify -Pintegration-test`.
- [x] 2.4 Document or configure the build so Testcontainers integration tests are skipped unless the integration-test profile is explicitly enabled.

## 3. Integration Test Infrastructure

- [x] 3.1 Add a test-only `BaseIntegrationTest` that starts MySQL and Redis Testcontainers and injects dynamic Spring properties with `@DynamicPropertySource`.
- [x] 3.2 Add RabbitMQ Testcontainers support only as needed for application context wiring, with Rabbit listeners disabled by default unless a specific integration test enables them.
- [x] 3.3 Initialize the MySQL container schema from `sql/schema.sql` or an equivalent project-controlled initializer, ensuring the schema initialization runs exactly once.
- [x] 3.4 Configure integration-test properties for stable test behavior, including test-friendly dish cache TTLs and Rabbit listener auto-startup defaults.
- [x] 3.5 Add explicit database cleanup utilities that remove business rows before each integration test in dependency order, or safely disable foreign-key checks when applicable, without relying on test execution order.
- [x] 3.6 Add Redis cleanup utilities that remove or flush test keys before each integration test.
- [x] 3.7 Avoid using `@Transactional` rollback as the only integration-test cleanup mechanism.

## 4. Test Data Helpers

- [x] 4.1 Add test-only helpers for creating users and administrators or inserting them directly when registration is not the behavior under test.
- [x] 4.2 Add helper support for logging in through MockMvc and extracting JWT tokens for authenticated requests.
- [x] 4.3 Add helper support for creating categories, dishes, dish stock, carts, orders, payments, and callback request bodies needed by the three first-pass flows.
- [x] 4.4 Add helper support for querying database state for assertions without relying on brittle auto-increment IDs.

## 5. Dish Cache Integration Tests

- [x] 5.1 Add `DishListCacheIT` or equivalent to verify cache miss on `/dish/list` queries real MySQL and writes a Redis cache value.
- [x] 5.2 Add a dish cache integration test proving a valid Redis cached value is returned while preserving category validation semantics, allowing `CategoryMapper` validation but avoiding the dish-list SQL path.
- [x] 5.3 Add a dish cache integration test proving an existing category with no available dishes caches JSON `[]`.
- [x] 5.4 Add a dish cache integration test proving dish create, update, or status mutation invalidates the affected category cache.
- [x] 5.5 Add a dish cache integration test proving written Redis keys have a positive TTL without waiting for the full expiration time.

## 6. Order Submit Integration Tests

- [x] 6.1 Add `OrderSubmitFlowIT` or equivalent to create authenticated user/cart/stock data through helpers and submit an order through the HTTP API.
- [x] 6.2 Verify successful order submit creates the order and order details in real MySQL.
- [x] 6.3 Verify successful order submit decreases available stock and increases locked stock.
- [x] 6.4 Verify repeating the same `Idempotency-Key` with the same effective request returns the original order result.
- [x] 6.5 Verify duplicate idempotent submit does not create another order and does not lock stock a second time.
- [x] 6.6 Verify insufficient stock rejects order submission without committing order rows, order detail rows, or negative stock quantities.

## 7. Payment Callback Integration Tests

- [x] 7.1 Add `PaymentCallbackFlowIT` or equivalent to submit an order, start mock payment, and post callbacks through the HTTP API.
- [x] 7.2 Verify a valid `SUCCESS` callback marks the order paid, marks the payment record successful, stores the callback record, and confirms locked stock.
- [x] 7.3 Verify repeated delivery of the same successful `callbackNo` is idempotent and does not duplicate stock confirmation records.
- [x] 7.4 Verify an amount mismatch callback records a terminal business result without paying the order or confirming locked stock.
- [x] 7.5 Verify a valid `FAILED` callback marks the payment failed while leaving the order pending payment and stock locked, following the current rule that failed payment does not cancel the order or release stock.

## 8. RabbitMQ Scope Verification

- [x] 8.1 Ensure the integration-test application context can start with RabbitMQ properties supplied by Testcontainers or with listeners disabled according to the test profile when RabbitMQ is needed.
- [x] 8.2 Treat timeout outbox assertions as optional in this stage; include them only if they fit naturally with application context or order submit setup.
- [x] 8.3 Do not wait for real RabbitMQ TTL/delayed-message expiration in this stage.
- [x] 8.4 Keep full TTL expiration, DLX routing, listener retry, and timeout cancellation release assertions out of this stage.

## 9. Documentation And Validation

- [x] 9.1 Update `docs/API_TEST.md` or an appropriate testing section with `./mvnw test` and `./mvnw verify -Pintegration-test` usage.
- [x] 9.2 Document that Docker is required only for the integration-test profile, not for default unit tests.
- [x] 9.3 Update `docs/PROJECT_CONTEXT.md` with the new Testcontainers regression suite status and commands.
- [x] 9.4 Run `./mvnw test` and confirm it succeeds without requiring Docker/Testcontainers.
- [x] 9.5 Run `./mvnw verify -Pintegration-test` and fix integration-test failures.
- [x] 9.6 Run `openspec validate add-testcontainers-regression-suite --strict`.
- [x] 9.7 Run `openspec validate --all --strict`.
