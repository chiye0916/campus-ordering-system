## Context

Order submission currently creates the order, order details, and clears the current user's cart inside one transaction. The request body only contains a remark; the actual order content is derived from the current user's shopping cart and refreshed dish data.

Without submit idempotency, these cases can create duplicate orders:

```text
user double-clicks submit
client request times out and retries
frontend accidentally sends the same submit action twice
```

This change protects only `POST /order/submit`. It does not solve overselling, stock locking, payment callback idempotency, or delayed timeout cancellation.

## Goals / Non-Goals

**Goals:**

- Require `Idempotency-Key` on `POST /order/submit`.
- Persist submit idempotency records in MySQL with a unique `(user_id, idempotency_key)` constraint.
- Return the original `orderId` for repeat requests with the same user, same key, and same request fingerprint.
- Reject same-key/different-content requests with `409`.
- Reject same-key requests while the first request is still processing with `409`.
- Keep successful API response shape as `Result<Long>`.
- Keep order creation, order details, cart cleanup, and idempotency success marking in a single transaction.

**Non-Goals:**

- Do not add inventory or stock locking.
- Do not add Redis idempotency cache.
- Do not add payment callback idempotency.
- Do not add RabbitMQ timeout cancellation.
- Do not change the successful response shape.
- Do not add user refund request behavior.

## Decisions

### Decision: Require `Idempotency-Key`

`POST /order/submit` will require a non-blank `Idempotency-Key` request header.

Missing or blank keys should return `400`.

Alternative considered: make the key optional and keep old behavior when missing. That is easier for existing callers, but it keeps the core trading endpoint vulnerable to duplicate submits and makes client behavior inconsistent.

### Decision: Keep `Result<Long>` For Successful Responses

Successful first submit and successful duplicate retry both return the order ID:

```json
{
  "code": 200,
  "message": "success",
  "data": 101
}
```

This preserves the current controller contract and keeps frontend changes small.

### Decision: Use A MySQL Idempotency Table First

Add `order_idempotency`:

```text
id
user_id
idempotency_key
request_hash
order_id
status
create_time
update_time
```

Recommended statuses:

```text
1 PROCESSING
2 SUCCEEDED
3 FAILED
```

The unique key is:

```text
uk_order_idempotency_user_key(user_id, idempotency_key)
```

MySQL is the source of truth for this change. Redis caching can be added later if submit traffic requires it.

Alternative considered: store idempotency only in Redis. That is fast, but it is weaker as a learning baseline because Redis expiry/loss could allow duplicate orders while MySQL orders remain permanent.

### Decision: Fingerprint The Effective Submit Content

The duplicate check should compare a `request_hash`.

Because the request body only has `remark` and the order content comes from the cart, the fingerprint should be based on stable effective submit content:

```text
userId
remark
cart items sorted by dishId
dishId
quantity
dishPrice
```

The idempotency key itself must not be part of the hash. If the same user reuses a key for different cart content or a different remark, the system returns `409`.

Alternative considered: hash only the request body. That would miss cart changes because the body currently does not include dish items.

### Decision: Insert PROCESSING Before Creating The Order

The intended flow is:

```text
Controller reads Idempotency-Key
  -> Service loads current user ID
  -> Service loads and refreshes cart items
  -> Service computes request_hash
  -> Try insert order_idempotency PROCESSING
     -> insert succeeds: create order in same transaction, update idempotency to SUCCEEDED with order_id, return order_id
     -> duplicate key: load existing record and compare request_hash/status
```

Duplicate key handling:

```text
hash differs       -> 409 idempotency key already used by different request
status SUCCEEDED  -> return existing order_id
status PROCESSING -> 409 request is processing, retry later
status FAILED     -> 409 previous request failed, use a new idempotency key
```

### Decision: Transaction Boundary Covers Business Result

For the first successful request, one transaction should cover:

```text
insert PROCESSING idempotency record
insert orders
insert order_detail
delete shopping_cart
update idempotency to SUCCEEDED with order_id
```

If order creation fails, the transaction should roll back and avoid marking an order as succeeded when the order was not created.

## Risks / Trade-offs

- [Risk] A process crash after committing a `PROCESSING` record but before success could block retries. -> Mitigation: first version returns `409` for `PROCESSING`; a later recovery change can add timeout/expiry handling.
- [Risk] Cart-based request hashes can change if dish price changes between retries. -> Mitigation: treat changed effective content as a different request and return `409`, which is safer than returning an unrelated order.
- [Risk] Existing clients that do not send `Idempotency-Key` will receive `400`. -> Mitigation: update frontend and API docs in the same change.
- [Risk] Concurrency around duplicate inserts can surface database duplicate-key exceptions. -> Mitigation: catch duplicate-key errors, load the existing idempotency record, and apply the duplicate handling rules.

## Migration Plan

1. Add `order_idempotency` DDL to `sql/schema.sql`.
2. Add idempotency entity, mapper interface, and MyBatis XML.
3. Update `OrderController.submit` to require `Idempotency-Key`.
4. Refactor `OrderService.submit` to accept the key and run the idempotency flow.
5. Add request hash generation based on current user, remark, and refreshed cart items.
6. Update frontend/API docs to send a stable key for each submit action.
7. Run `./mvnw test` and targeted API verification.

Rollback strategy: remove the controller key requirement and idempotency service integration. The new table can remain unused or be dropped in local development.

## Open Questions

- None. The agreed behavior is: key required, duplicate success returns the original order ID, same-key/different-content returns `409`.
