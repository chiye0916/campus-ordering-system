## ADDED Requirements

### Requirement: Dish List Cache Uses Category Key
The system SHALL cache `/dish/list` results by category using a stable Redis key derived from the category ID.

#### Scenario: Cache key uses category ID
- **WHEN** the system reads, writes, or evicts the dish list cache for category ID `10`
- **THEN** it MUST use the key `dish:list:category:10`

#### Scenario: Category validation happens before cache lookup
- **WHEN** `/dish/list` is requested for a category ID that does not exist
- **THEN** the system MUST return the existing category-not-found business error
- **AND** it MUST NOT create a dish-list cache entry for that category ID

### Requirement: Dish List Cache Hit Returns Cached Data
The system SHALL return cached dish-list data when Redis contains valid JSON for the requested category.

#### Scenario: Cache hit avoids database dish query
- **WHEN** `/dish/list` is requested for an existing category
- **AND** Redis contains a valid cached JSON array for that category key
- **THEN** the system MUST deserialize and return the cached dish list
- **AND** it MUST NOT query the dish table for available dishes in that category
- **AND** it MAY still query the category table first to preserve the existing category validation semantics

#### Scenario: Cached empty list is a cache hit
- **WHEN** `/dish/list` is requested for an existing category
- **AND** Redis contains JSON `[]` for that category key
- **THEN** the system MUST treat the cached empty array as a cache hit
- **AND** it MUST return an empty list
- **AND** it MUST NOT query the dish table for available dishes in that category

### Requirement: Dish List Cache Miss Queries Database And Writes Cache
The system SHALL query the database and write Redis cache when no cached dish-list value exists.

#### Scenario: Cache miss stores non-empty list
- **WHEN** `/dish/list` is requested for an existing category
- **AND** Redis has no value for that category key
- **AND** the database returns one or more available dishes
- **THEN** the system MUST return the database result
- **AND** it MUST write the list to Redis using the configured normal list TTL

#### Scenario: Normal list TTL defaults to thirty minutes
- **WHEN** the system writes a non-empty dish-list cache entry
- **THEN** it MUST use `dish.cache.list-ttl`
- **AND** the default value MUST be 30 minutes

### Requirement: Empty Dish List Is Cached
The system SHALL cache an empty dish-list result as JSON `[]` when the category exists and has no available dishes.

#### Scenario: Existing category with no available dishes caches empty list
- **WHEN** `/dish/list` is requested for an existing category
- **AND** Redis has no value for that category key
- **AND** the database returns no available dishes
- **THEN** the system MUST return an empty list
- **AND** it MUST write JSON `[]` to Redis
- **AND** it MUST use the configured empty list TTL

#### Scenario: Empty list TTL defaults to five minutes
- **WHEN** the system writes an empty dish-list cache entry
- **THEN** it MUST use `dish.cache.empty-list-ttl`
- **AND** the default value MUST be 5 minutes

### Requirement: Cache Read Distinguishes Miss From Empty Hit
The system SHALL distinguish a missing dish-list cache entry from a cached empty dish list.

#### Scenario: Missing key is a cache miss
- **WHEN** the dish-list cache component reads a category key that does not exist in Redis
- **THEN** it MUST return a cache miss result
- **AND** the caller MUST query the dish table for available dishes

#### Scenario: Empty JSON array is a cache hit
- **WHEN** the dish-list cache component reads JSON `[]` from Redis
- **THEN** it MUST return a cache hit result containing an empty list
- **AND** the caller MUST NOT query the dish table solely because the cached list is empty

### Requirement: Redis Read Failure Falls Back To Database
The system SHALL treat Redis read failures as non-blocking cache failures for `/dish/list`.

#### Scenario: Redis get failure still returns database result
- **WHEN** `/dish/list` is requested for an existing category
- **AND** reading Redis for the category key fails
- **THEN** the system MUST log the cache read failure
- **AND** it MUST query the database for available dishes
- **AND** it MUST return the database result instead of failing the API because of Redis

### Requirement: Redis Write Failure Does Not Fail Dish List
The system SHALL treat Redis write failures as non-blocking cache failures for `/dish/list`.

#### Scenario: Redis set failure still returns database result
- **WHEN** `/dish/list` is requested for an existing category
- **AND** the system queries the database because of a cache miss or cache read failure
- **AND** writing the database result to Redis fails
- **THEN** the system MUST log the cache write failure
- **AND** it MUST return the database result instead of failing the API because of Redis

### Requirement: Corrupted Dish List Cache Is Recovered
The system SHALL recover from invalid cached JSON without exposing the parsing failure to users.

#### Scenario: Corrupted cached JSON is deleted and database result returned
- **WHEN** `/dish/list` is requested for an existing category
- **AND** Redis contains a value for that category key
- **AND** the cached value cannot be deserialized as a dish-list JSON array
- **THEN** the system MUST log the corrupted cache value
- **AND** it MUST attempt to delete the corrupted category key
- **AND** it MUST query the database for available dishes
- **AND** it MUST return the database result
- **AND** it MUST attempt to rewrite the cache with the database result

#### Scenario: Corrupted cache delete failure does not fail dish list
- **WHEN** corrupted cached JSON is detected
- **AND** deleting the corrupted Redis key fails
- **THEN** the system MUST log the delete failure
- **AND** it MUST still query the database and return the database result

### Requirement: Dish Mutations Evict Dish List Cache
The system SHALL evict affected dish-list cache keys after successful dish create, update, and status changes.

#### Scenario: Create dish evicts new category cache
- **WHEN** an administrator successfully creates a dish in a category
- **THEN** the system MUST evict the dish-list cache key for that category

#### Scenario: Update dish evicts unchanged category cache
- **WHEN** an administrator successfully updates a dish without changing its category
- **THEN** the system MUST evict the dish-list cache key for the dish's current category

#### Scenario: Update dish evicts old and new category cache
- **WHEN** an administrator successfully updates a dish and changes its category
- **THEN** the system MUST evict the dish-list cache key for the old category
- **AND** it MUST evict the dish-list cache key for the new category

#### Scenario: Status change evicts current category cache
- **WHEN** an administrator successfully changes a dish status
- **THEN** the system MUST evict the dish-list cache key for the dish's current category

#### Scenario: Stock update does not evict dish list cache
- **WHEN** an administrator successfully updates dish stock
- **THEN** the system MUST NOT evict `/dish/list` cache solely because of the stock update

### Requirement: Redis Eviction Failure Does Not Roll Back Dish Mutations
The system SHALL treat Redis eviction failures as non-blocking after successful dish mutations.

#### Scenario: Create succeeds when cache eviction fails
- **WHEN** an administrator creates a dish successfully in the database
- **AND** Redis deletion for the affected category cache fails
- **THEN** the system MUST log the cache eviction failure
- **AND** it MUST still return a successful create result

#### Scenario: Update succeeds when cache eviction fails
- **WHEN** an administrator updates a dish successfully in the database
- **AND** Redis deletion for an affected category cache fails
- **THEN** the system MUST log the cache eviction failure
- **AND** it MUST still return a successful update result

#### Scenario: Status change succeeds when cache eviction fails
- **WHEN** an administrator changes a dish status successfully in the database
- **AND** Redis deletion for the affected category cache fails
- **THEN** the system MUST log the cache eviction failure
- **AND** it MUST still return a successful status-change result

### Requirement: Dish List Cache Logic Is Encapsulated
The system SHALL encapsulate dish-list cache operations outside of `DishServiceImpl` business orchestration.

#### Scenario: Dish service delegates cache details
- **WHEN** `DishServiceImpl` lists, creates, updates, or changes status for dishes
- **THEN** key construction, Redis get/set/delete calls, TTL selection, JSON serialization, JSON deserialization, and cache failure logging MUST be handled by a dedicated dish-list cache component
- **AND** `DishServiceImpl` MUST remain responsible for category validation, dish database queries, and dish mutation business rules
