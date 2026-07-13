## 1. Configuration

- [x] 1.1 Add a `DishCacheProperties` configuration properties class with `listTtl` defaulting to 30 minutes and `emptyListTtl` defaulting to 5 minutes.
- [x] 1.2 Enable the dish cache properties through existing Spring Boot configuration patterns.
- [x] 1.3 Add `dish.cache.list-ttl=30m` and `dish.cache.empty-list-ttl=5m` to `src/main/resources/application.properties`.

## 2. Cache Component

- [x] 2.1 Add a `DishListCacheService` component for dish-list cache key construction, Redis reads, Redis writes, Redis deletes, JSON serialization, JSON deserialization, TTL selection, and cache failure logging.
- [x] 2.2 Implement `dish:list:category:{categoryId}` key construction in the cache component.
- [x] 2.3 Implement cache read as `Optional<List<DishVO>>` or an equivalent cache result so cache miss is distinct from a cached empty list.
- [x] 2.4 Implement cache write so non-empty lists use `dish.cache.list-ttl` and empty lists are written as JSON `[]` using `dish.cache.empty-list-ttl`.
- [x] 2.5 Implement Redis read failure fallback so get exceptions are logged and treated as cache misses.
- [x] 2.6 Implement Redis write failure fallback so set exceptions are logged and do not fail the caller.
- [x] 2.7 Implement corrupted JSON handling so parse failures are logged, the bad key is deleted when possible, and the caller treats the result as a cache miss.
- [x] 2.8 Implement Redis delete failure fallback so eviction exceptions are logged with operation/category context and do not fail the caller.

## 3. Dish Service Integration

- [x] 3.1 Refactor `DishServiceImpl.list` to validate category existence first, then use `DishListCacheService` for cache read/write around the existing DB query.
- [x] 3.2 Preserve missing-category behavior for `/dish/list` and ensure missing categories do not create dish-list cache entries.
- [x] 3.3 Refactor dish create cache invalidation to evict the newly created dish category through `DishListCacheService`.
- [x] 3.4 Refactor dish update cache invalidation to use the database-loaded existing dish category, evict the old category, and evict the new category when the category changes.
- [x] 3.5 Refactor dish status cache invalidation to use the database-loaded existing dish category and evict that category after successful status update.
- [x] 3.6 Confirm administrator stock set/query flows do not evict `/dish/list` cache because `DishVO` does not include stock.
- [x] 3.7 Remove direct `StringRedisTemplate` and `ObjectMapper` dish-list cache responsibilities from `DishServiceImpl` after delegation is in place.

## 4. Unit Tests

- [x] 4.1 Add cache component tests for key construction and default TTL selection for non-empty and empty lists.
- [x] 4.2 Add a test proving cache hit returns cached dish data and avoids querying `DishMapper.selectAvailableByCategoryId`, while still allowing category validation.
- [x] 4.3 Add a test proving cache miss queries the database and writes a non-empty list to Redis with the normal TTL.
- [x] 4.4 Add a test proving an existing category with no available dishes returns an empty list and writes JSON `[]` with the empty-list TTL.
- [x] 4.5 Add a test proving Redis get failure logs/falls back to the database and still returns the database result.
- [x] 4.6 Add a test proving Redis set failure does not fail `/dish/list` after the database result is loaded.
- [x] 4.7 Add a cache component test proving cached JSON `[]` is returned as a cache hit and is distinct from a missing Redis key.
- [x] 4.8 Add a cache component test proving corrupted cached JSON deletes the bad key and returns a cache miss result.
- [x] 4.9 Add a service test proving corrupted cached JSON causes DB fallback and cache rewrite through `DishServiceImpl.list`.
- [x] 4.10 Add a test proving corrupted cache delete failure still returns the database result.
- [x] 4.11 Add tests proving create, update without category change, update with category change, and status change evict the expected category keys.
- [x] 4.12 Add tests proving Redis delete failure does not fail create, update, or status-change flows.
- [x] 4.13 Add a test proving `/dish/list` for a missing category returns the existing business error and does not read or write dish-list cache.

## 5. Documentation And Validation

- [x] 5.1 Update `docs/API_TEST.md` with concise notes for `/dish/list` cache TTL, empty-list caching, Redis failure fallback, corrupted-cache recovery, mutation invalidation behavior, and Cache Aside eventual consistency.
- [x] 5.2 Update `docs/PROJECT_CONTEXT.md` with the enhanced dish-list cache behavior.
- [x] 5.3 Fix the stale `docs/PROJECT_CONTEXT.md` description that still says `add-payment-callback-idempotency` is the active unarchived change.
- [x] 5.4 Run `./mvnw test` and fix compile or test failures.
- [x] 5.5 Run `openspec validate enhance-redis-dish-cache --strict`.
- [x] 5.6 Run `openspec validate --all --strict`.
