## Context

`GET /dish/list?categoryId=...` is the project entry point for users browsing available dishes by category. The current implementation already follows a basic Cache Aside pattern inside `DishServiceImpl`:

```text
DishController.list
  -> DishServiceImpl.list
     -> CategoryMapper.selectById
     -> Redis get dish:list:category:{categoryId}
     -> DishMapper.selectAvailableByCategoryId on cache miss
     -> Redis set
```

Dish create, update, and status changes also delete the corresponding category cache key. This is a good starting point, but cache details are mixed into business code and Redis/cache failures can currently affect core dish flows. This stage improves the existing cache behavior without changing the public `/dish/list` semantics.

## Goals / Non-Goals

**Goals:**

- Keep `/dish/list` response shape and category validation semantics unchanged.
- Add TTL configuration for normal and empty dish-list cache entries.
- Cache empty lists as JSON `[]` when the category exists.
- Make Redis read/write/delete failures non-blocking for dish list and dish mutation flows.
- Recover from corrupted cached JSON by evicting the bad key when possible, querying the database, and rewriting cache.
- Extract cache key construction, serialization, TTL selection, Redis fallback, corrupted JSON handling, and eviction into a focused dish-list cache component.
- Ensure dish create/update/status flows evict the correct category cache keys based on database truth.
- Cover cache hit, miss, empty list, corrupted JSON, Redis failures, and mutation invalidation with focused unit tests.

**Non-Goals:**

- Do not change `/dish/list` API request parameters, response shape, authentication, or category-not-found behavior.
- Do not cache dish details, category lists, stock, order data, or payment data.
- Do not add cache warmup, cache-breakdown mutex locks, TTL jitter as a required behavior, Redis Lua, Redisson, Caffeine/multi-level cache, pub/sub, or MQ broadcast invalidation.
- Do not introduce Testcontainers in this stage.
- Do not add database schema changes.

## Decisions

### Decision: Use A Focused DishListCacheService

Add a small cache component, tentatively named `DishListCacheService`, to own:

- `dish:list:category:{categoryId}` key construction
- reading cached `List<DishVO>` while distinguishing cache miss from cached empty list
- writing cached `List<DishVO>` with the correct TTL
- evicting by category ID
- logging Redis failures
- handling JSON parse failures

`DishServiceImpl` should continue to own business orchestration:

```text
DishServiceImpl.list
  validate category exists
  ask DishListCacheService for cached list
  if hit: return cached list
  query DishMapper
  ask DishListCacheService to cache result
  return DB result
```

The read method should return `Optional<List<DishVO>>` or an equivalent cache result object:

```text
Optional.empty()
  cache miss, Redis read failure, or corrupted JSON fallback

Optional.of(emptyList())
  cache hit containing JSON []

Optional.of(nonEmptyList)
  cache hit containing available dishes
```

This distinction is required because a cached empty list is a real cache hit and must not be confused with a missing Redis key. This keeps Controller -> Service -> Mapper/XML -> DB layering intact. The cache service does not call mappers and does not decide whether a category exists.

Because existing semantics require category validation before cache use, a cache hit may still involve `CategoryMapper.selectById`. Cache hits should avoid the dish-list database query, not necessarily every database query.

### Decision: Configure TTLs With Dish Cache Properties

Use Spring Boot `@ConfigurationProperties` for dish cache settings, with defaults:

```properties
dish.cache.list-ttl=30m
dish.cache.empty-list-ttl=5m
```

The implementation should use `Duration` fields so property values match Spring Boot duration syntax. This mirrors the existing `order.timeout.*` configuration style and avoids scattering magic numbers in service code.

TTL jitter is not required in this stage. It can be added later if cache avalanche risk becomes real enough to justify another small change.

### Decision: Empty Lists Are Cached As JSON Arrays

When a category exists and has no available dishes, the system should cache the response as JSON `[]` with the empty-list TTL. This keeps the cached representation aligned with the API return type and avoids special sentinel strings such as `EMPTY` or `NULL`.

Category-not-found behavior remains a business concern handled before cache lookup. Missing categories must still return the existing 404 behavior and must not create dish-list cache entries.

### Decision: Redis Is An Acceleration Layer, Not The Source Of Truth

Redis failures should not turn core dish operations into 500 responses:

```text
Redis get fails:
  log warning
  query database
  try to write cache
  return database result

Redis set fails:
  log warning
  return database result

Redis delete fails:
  log warning
  do not roll back dish create/update/status database changes
```

Delete failures may leave stale data visible until TTL expiration. This is acceptable for this learning-stage cache because the database remains the source of truth and TTL provides bounded eventual cleanup. Logs should include enough context to debug invalidation failures, such as operation, dish ID when available, category ID, cache key, and exception message.

The dish-list cache follows Cache Aside and does not provide strong cache/database consistency. Cache eviction failures do not roll back database mutations; stale cache may exist until TTL expires.

### Decision: Corrupted JSON Falls Back To Database

If Redis returns a value but JSON deserialization fails, the cache component should:

```text
log warning
try to delete the corrupted key
return cache miss to caller
```

`DishServiceImpl` then queries the database and asks the cache component to write a fresh value. If deleting or rewriting the bad key fails, the API should still return the database result.

### Decision: Mutation Invalidation Uses Database-Derived Category IDs

Cache eviction should be based on data already loaded from the database, not frontend input:

- create: after successful insert, evict the new category key
- update: load existing dish first, then after successful update evict the old category key and evict the new category key when the category changed
- status update: load existing dish first, then after successful status update evict that dish's current category key

Admin stock updates must not evict `/dish/list` cache because the current `DishVO` list response does not include stock.

## Risks / Trade-offs

- [Risk] Redis delete failure can leave stale dish-list data until TTL expiry. -> Mitigation: log detailed warnings and keep TTL bounded.
- [Risk] Falling back to the database during Redis outage increases database load. -> Mitigation: this stage prioritizes correctness and availability; load-oriented improvements such as mutex locks and multi-level cache remain future work.
- [Risk] Catching Redis exceptions too broadly can hide operational issues. -> Mitigation: log warnings with cache key and operation context while preserving user-facing success.
- [Risk] Cache service tests may overfit implementation details. -> Mitigation: test externally visible cache behavior and interactions at the service boundary, especially hit/miss, fallback, and invalidation outcomes.
