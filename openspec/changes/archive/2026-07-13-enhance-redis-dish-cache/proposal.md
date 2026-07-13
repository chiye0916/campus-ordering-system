## Why

`/dish/list?categoryId=...` already uses a Redis cache, but the current behavior has no TTL, no explicit empty-list caching rule, no Redis failure fallback, and limited test coverage. This change makes the existing dish-list cache reliable and observable before the project moves on to broader automated regression and reliability work.

## What Changes

- Add configurable TTLs for dish-list cache entries:
  - normal non-empty list TTL defaults to 30 minutes
  - empty list TTL defaults to 5 minutes
- Cache empty dish-list results as JSON `[]` when the category exists but has no available dishes.
- Preserve the existing `/dish/list` business semantics: category existence is still validated first, missing categories still return 404, and this stage does not change response shape.
- Add Redis failure fallback for dish-list cache operations:
  - Redis read failure logs and falls back to database query
  - Redis write failure logs and does not fail the API response
  - Redis delete failure logs a warning and does not roll back dish create/update/status flows
- Handle corrupted cached JSON by deleting the bad key when possible, querying the database, and rewriting the cache without exposing the parse failure to users.
- Extract dish-list cache details into a small cache component so `DishServiceImpl` keeps business orchestration focused.
- Strengthen and test cache invalidation rules for dish create, update, and status changes, including old/new category eviction on category changes.
- Update project/API documentation during implementation, including the stale project-context note that the previous payment callback change is still active.
- Keep verification focused on Mockito/unit tests plus existing Maven/OpenSpec validation.
- Out of scope: dish detail cache, stock cache, cache warmup, cache penetration/breakdown mutex locks, TTL jitter as a required feature, Redis Lua, Redisson, Caffeine or multi-level cache, Redis pub/sub or MQ broadcast invalidation, Testcontainers, and changing `/dish/list` API semantics.

## Capabilities

### New Capabilities

- `dish-list-cache`: Defines `/dish/list` Redis Cache Aside behavior, TTLs, empty-list caching, Redis failure fallback, corrupted JSON recovery, and dish mutation invalidation rules.

### Modified Capabilities

- None.

## Impact

- Affected API behavior: `GET /dish/list` keeps the same request/response semantics but becomes resilient to Redis failures and corrupted cached JSON.
- Affected configuration: add dish-list cache TTL properties under the dish cache configuration namespace.
- Affected backend code: dish service cache integration, a new dish-list cache component/properties class, logging around cache failures, and focused service/unit tests.
- Affected docs: `docs/API_TEST.md` and `docs/PROJECT_CONTEXT.md` should describe the enhanced cache behavior and correct the previous-stage stale active-change description.
