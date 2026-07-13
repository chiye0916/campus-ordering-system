## ADDED Requirements

### Requirement: Integration Tests Run Separately From Unit Tests
The system SHALL provide a Testcontainers integration-test workflow that is separate from the default unit-test workflow.

#### Scenario: Unit tests do not require Docker
- **WHEN** a developer runs `./mvnw test`
- **THEN** Maven MUST run the normal unit tests
- **AND** it MUST NOT require Docker or Testcontainers containers to be available

#### Scenario: Integration tests run through explicit profile
- **WHEN** a developer runs `./mvnw verify -Pintegration-test`
- **THEN** Maven MUST run the Testcontainers integration tests
- **AND** Docker MUST be required for that command

#### Scenario: Integration tests use IT naming
- **WHEN** integration test classes are added
- **THEN** they MUST be named with an `IT` suffix
- **AND** they MUST be executed by the integration-test workflow rather than the default unit-test workflow

#### Scenario: Surefire excludes integration tests
- **WHEN** a developer runs `./mvnw test`
- **THEN** Maven Surefire MUST NOT execute `*IT` integration test classes

#### Scenario: Failsafe includes integration tests
- **WHEN** a developer runs `./mvnw verify -Pintegration-test`
- **THEN** Maven Failsafe MUST execute `*IT` integration test classes

### Requirement: Testcontainers Infrastructure Uses Real Services
The integration-test workflow SHALL run against container-backed infrastructure instead of pre-existing local services.

#### Scenario: MySQL and Redis containers provide test services
- **WHEN** integration tests start
- **THEN** they MUST use Testcontainers-managed MySQL and Redis services
- **AND** they MUST inject those service connection properties into the Spring test context dynamically

#### Scenario: Redis uses generic container
- **WHEN** Redis is added to the Testcontainers integration-test infrastructure
- **THEN** it MUST use a `GenericContainer` or equivalent generic container setup with a Redis image such as `redis:7-alpine`
- **AND** it MUST NOT assume a dedicated Redis Testcontainers module exists

#### Scenario: RabbitMQ container is lightweight and optional in scope
- **WHEN** the Spring test context or existing messaging configuration requires RabbitMQ connection properties
- **THEN** the integration-test infrastructure MUST provide a Testcontainers-managed RabbitMQ service or disable listeners where appropriate
- **AND** it MUST NOT require a locally running RabbitMQ service

#### Scenario: Local data is not used
- **WHEN** integration tests run
- **THEN** they MUST NOT depend on locally running MySQL, Redis, RabbitMQ, or pre-existing local database rows

### Requirement: Integration Test Database Schema Is Initialized
The integration-test workflow SHALL initialize the container database schema from the project schema.

#### Scenario: Schema initializes from project SQL
- **WHEN** the MySQL integration-test container starts
- **THEN** the schema MUST be initialized from `sql/schema.sql` or an equivalent project-controlled schema initializer

#### Scenario: Schema initialization runs once
- **WHEN** the integration-test database schema is initialized
- **THEN** the same schema script MUST NOT be executed by both a Testcontainers init script and Spring SQL initialization in the same test context

#### Scenario: Mapper XML uses real database schema
- **WHEN** integration tests execute service or HTTP flows that call mappers
- **THEN** MyBatis XML statements MUST run against the real container MySQL schema

### Requirement: Integration Tests Clean State Explicitly
The integration-test workflow SHALL isolate test methods through explicit cleanup instead of test order or pre-existing data.

#### Scenario: Database state is cleaned between tests
- **WHEN** an integration test method starts
- **THEN** database rows from previous integration tests MUST be removed or reset before test data is inserted
- **AND** cleanup MUST delete or truncate tables in dependency order or safely disable foreign-key checks when applicable

#### Scenario: Redis state is cleaned between tests
- **WHEN** an integration test method starts
- **THEN** Redis keys created by previous integration tests MUST be removed or flushed from the test Redis database

#### Scenario: Tests do not rely on execution order
- **WHEN** integration tests are run in any order
- **THEN** each test MUST create the data it needs or use explicit helper setup

#### Scenario: Transaction rollback is not the only cleanup
- **WHEN** integration tests use MockMvc, committed database state, Redis, or messaging side effects
- **THEN** they MUST NOT rely solely on `@Transactional` test rollback as the cleanup mechanism

### Requirement: Integration Test Helpers Are Test-Only
The system SHALL provide lightweight test-only helpers for integration test setup and assertions.

#### Scenario: Helpers create common domain data
- **WHEN** integration tests need users, categories, dishes, stock, carts, orders, or payments
- **THEN** test source helpers MAY create that data
- **AND** those helpers MUST NOT be part of production code

#### Scenario: Authentication can be exercised through HTTP
- **WHEN** an integration flow needs an authenticated user or administrator
- **THEN** test helpers MUST be able to obtain a JWT token through the application API or an equivalent test-only setup that still exercises the request authentication path used by the flow

### Requirement: Dish Cache Integration Regression
The integration-test suite SHALL cover the dish-list cache flow against real Redis and real MySQL.

#### Scenario: Cache miss writes Redis
- **WHEN** an integration test requests `/dish/list` for an existing category with available dishes
- **AND** Redis has no dish-list key for that category
- **THEN** the API MUST return the database dish list
- **AND** Redis MUST contain a dish-list cache value for that category

#### Scenario: Cache hit uses Redis value
- **WHEN** an integration test requests `/dish/list` for an existing category
- **AND** Redis contains valid dish-list JSON for that category
- **THEN** the API MUST return the cached value
- **AND** it MUST preserve the existing category validation behavior
- **AND** category validation MAY still query the category table

#### Scenario: Empty list is cached
- **WHEN** an integration test requests `/dish/list` for an existing category with no available dishes
- **THEN** the API MUST return an empty list
- **AND** Redis MUST store JSON `[]` for that category key

#### Scenario: Dish mutation invalidates cache
- **WHEN** an integration test changes a dish through create, update, or status APIs
- **THEN** the affected dish-list category cache MUST be evicted or refreshed according to the dish-list cache rules

#### Scenario: Dish cache TTL exists
- **WHEN** the dish-list cache is written in Redis
- **THEN** the Redis key MUST have a positive TTL
- **AND** the test MUST NOT wait for the full configured expiration time

### Requirement: Order Submit Integration Regression
The integration-test suite SHALL cover order submission, idempotency, and stock locking against real MySQL.

#### Scenario: Submit order locks stock
- **WHEN** an authenticated user adds available dishes to the cart and submits an order
- **THEN** an order and order details MUST be created
- **AND** dish stock available quantity MUST decrease
- **AND** dish stock locked quantity MUST increase

#### Scenario: Duplicate idempotency key does not duplicate order
- **WHEN** the same user repeats `POST /order/submit` with the same `Idempotency-Key` and same effective cart request
- **THEN** the system MUST return the original order result
- **AND** it MUST NOT create another order
- **AND** it MUST NOT lock stock a second time

#### Scenario: Insufficient stock fails without order
- **WHEN** an authenticated user submits an order for more stock than is available
- **THEN** the system MUST reject the order
- **AND** it MUST NOT create a committed order or order detail
- **AND** stock quantities MUST NOT become negative

### Requirement: Payment Callback Integration Regression
The integration-test suite SHALL cover mock payment initiation and callback processing against real MySQL and Redis.

#### Scenario: Successful callback pays order and confirms stock
- **WHEN** an authenticated user submits an order, starts mock payment, and posts a valid `SUCCESS` callback
- **THEN** the order MUST become paid
- **AND** the payment record MUST become successful
- **AND** the callback record MUST be stored
- **AND** locked stock MUST be confirmed

#### Scenario: Duplicate callback is idempotent
- **WHEN** a valid successful callback has already processed an order
- **AND** the same `callbackNo` is delivered again
- **THEN** the order MUST NOT be paid a second time
- **AND** stock confirmation records MUST NOT be duplicated

#### Scenario: Amount mismatch does not pay order
- **WHEN** a mock payment callback amount does not match the payment record amount
- **THEN** the callback MUST be recorded as a terminal business result
- **AND** the order MUST remain pending payment
- **AND** locked stock MUST NOT be confirmed

#### Scenario: Failed callback leaves order pending
- **WHEN** a valid `FAILED` payment callback is processed for a paying payment record
- **THEN** the payment record MUST become failed
- **AND** the order MUST remain pending payment
- **AND** stock MUST remain locked
- **AND** this MUST follow the current payment rule that failed payment does not cancel the order or release locked stock

### Requirement: RabbitMQ Regression Scope Is Limited
The integration-test suite SHALL avoid full RabbitMQ timeout-cancellation regression in this stage.

#### Scenario: RabbitMQ startup does not imply delayed timeout test
- **WHEN** RabbitMQ is started for integration tests
- **THEN** the tests MAY verify application context wiring or timeout outbox persistence
- **AND** they MUST NOT be required to wait for TTL expiration, assert DLX routing, or run the full timeout-cancel consumer flow in this stage

#### Scenario: Timeout outbox assertions are optional
- **WHEN** the first integration suite is implemented
- **THEN** timeout outbox assertions MAY be included only if they fit naturally with application context or order submit setup
- **AND** they MUST NOT block completion of the three primary integration flows

### Requirement: Stage Cleanup Is Included
The change SHALL clean up stale documentation and spec context discovered after the previous stage.

#### Scenario: Project context has no stale active change
- **WHEN** this change updates project context
- **THEN** `docs/PROJECT_CONTEXT.md` MUST NOT claim that `enhance-redis-dish-cache` is the active change

#### Scenario: Dish list cache purpose is no longer TBD
- **WHEN** this change updates OpenSpec specs
- **THEN** `openspec/specs/dish-list-cache/spec.md` MUST have a meaningful Purpose instead of the archived `TBD` placeholder
