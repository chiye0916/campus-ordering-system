## 1. Database

- [ ] 1.1 Add `payment_record` table DDL with order identity, user identity, amount, channel, trade numbers, status, timestamps, and indexes.
- [ ] 1.2 Decide whether the first implementation should enforce one successful payment per order with a database constraint or leave it to later idempotency work.

## 2. Domain And Persistence

- [ ] 2.1 Create `PaymentRecord` entity using `BigDecimal` for amount and `LocalDateTime` for timestamps.
- [ ] 2.2 Create `PaymentRecordMapper` with insert and status update methods.
- [ ] 2.3 Create `PaymentRecordMapper.xml` using MyBatis XML SQL.
- [ ] 2.4 Add or reuse a utility for generating internal payment trade numbers.

## 3. Mock Payment Flow

- [ ] 3.1 Update `OrderServiceImpl.pay()` to run the mock payment record creation and order status update in one transaction.
- [ ] 3.2 Create a `MOCK` payment record before the order status transition.
- [ ] 3.3 Keep `OrdersMapper.updateToPaidById(...)` with `where status = 1` as the final state guard.
- [ ] 3.4 Mark the payment record successful only after the order status update succeeds.
- [ ] 3.5 Ensure rejected payments do not leave a successful payment record.

## 4. Verification And Documentation

- [ ] 4.1 Run `./mvnw test` and fix any compile/test failures.
- [ ] 4.2 Update `docs/API_TEST.md` with SQL checks for `payment_record` after mock payment.
- [ ] 4.3 Manually verify `PUT /order/{id}/pay` still returns the existing `OrderPayVO` shape.
- [ ] 4.4 Manually verify the database contains a successful `MOCK` payment record for a paid order.
