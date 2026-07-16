package demo3.demo3_068.service.impl;

import demo3.demo3_068.common.BaseContext;
import demo3.demo3_068.common.Constants;
import demo3.demo3_068.common.PageResult;
import demo3.demo3_068.common.PermissionChecker;
import demo3.demo3_068.common.RedisDistributedLock;
import demo3.demo3_068.dto.OrderPageQueryDTO;
import demo3.demo3_068.dto.OrderSubmitDTO;
import demo3.demo3_068.entity.Dish;
import demo3.demo3_068.entity.OrderIdempotency;
import demo3.demo3_068.entity.OrderDetail;
import demo3.demo3_068.entity.Orders;
import demo3.demo3_068.entity.PaymentRecord;
import demo3.demo3_068.entity.ShoppingCart;
import demo3.demo3_068.mapper.DishMapper;
import demo3.demo3_068.exception.BusinessException;
import demo3.demo3_068.mapper.OrderDetailMapper;
import demo3.demo3_068.mapper.OrderIdempotencyMapper;
import demo3.demo3_068.mapper.OrdersMapper;
import demo3.demo3_068.mapper.PaymentRecordMapper;
import demo3.demo3_068.mapper.RefundRequestMapper;
import demo3.demo3_068.mapper.ShoppingCartMapper;
import demo3.demo3_068.mapper.UserMapper;
import demo3.demo3_068.model.OrderIdempotencyStatus;
import demo3.demo3_068.model.OrderStatus;
import demo3.demo3_068.model.OrderStatusChangeOperation;
import demo3.demo3_068.model.PaymentStatus;
import demo3.demo3_068.model.Role;
import demo3.demo3_068.observability.BusinessMetrics;
import demo3.demo3_068.observability.TraceContext;
import demo3.demo3_068.service.DishStockService;
import demo3.demo3_068.service.OrderService;
import demo3.demo3_068.service.OrderStatusHistoryService;
import demo3.demo3_068.service.OrderTimeoutOutboxService;
import demo3.demo3_068.utils.OrderNumberUtil;
import demo3.demo3_068.utils.PaymentTradeNoUtil;
import demo3.demo3_068.vo.OrderDetailVO;
import demo3.demo3_068.vo.OrderItemVO;
import demo3.demo3_068.vo.OrderPayVO;
import demo3.demo3_068.vo.OrderStatusHistoryVO;
import demo3.demo3_068.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    private static final String PAYMENT_CHANNEL_MOCK = "MOCK";
    private static final String TIMEOUT_RELEASE_REMARK = "订单超时自动取消释放库存";
    private static final Duration ORDER_STATUS_LOCK_TTL = Duration.ofSeconds(30);

    private final OrdersMapper ordersMapper;
    private final OrderDetailMapper orderDetailMapper;
    private final OrderIdempotencyMapper orderIdempotencyMapper;
    private final ShoppingCartMapper shoppingCartMapper;
    private final DishMapper dishMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final RefundRequestMapper refundRequestMapper;
    private final UserMapper userMapper;
    private final RedisDistributedLock redisDistributedLock;
    private final DishStockService dishStockService;
    private final OrderTimeoutOutboxService orderTimeoutOutboxService;
    private final OrderStatusHistoryService orderStatusHistoryService;
    private final BusinessMetrics businessMetrics;

    public OrderServiceImpl(OrdersMapper ordersMapper,
                            OrderDetailMapper orderDetailMapper,
                            OrderIdempotencyMapper orderIdempotencyMapper,
                            ShoppingCartMapper shoppingCartMapper,
                            DishMapper dishMapper,
                            PaymentRecordMapper paymentRecordMapper,
                            RefundRequestMapper refundRequestMapper,
                            UserMapper userMapper,
                            RedisDistributedLock redisDistributedLock,
                            DishStockService dishStockService,
                            OrderTimeoutOutboxService orderTimeoutOutboxService,
                            OrderStatusHistoryService orderStatusHistoryService,
                            BusinessMetrics businessMetrics) {
        this.ordersMapper = ordersMapper;
        this.orderDetailMapper = orderDetailMapper;
        this.orderIdempotencyMapper = orderIdempotencyMapper;
        this.shoppingCartMapper = shoppingCartMapper;
        this.dishMapper = dishMapper;
        this.paymentRecordMapper = paymentRecordMapper;
        this.refundRequestMapper = refundRequestMapper;
        this.userMapper = userMapper;
        this.redisDistributedLock = redisDistributedLock;
        this.dishStockService = dishStockService;
        this.orderTimeoutOutboxService = orderTimeoutOutboxService;
        this.orderStatusHistoryService = orderStatusHistoryService;
        this.businessMetrics = businessMetrics;
    }

    @Override
    @Transactional
    public Long submit(OrderSubmitDTO orderSubmitDTO, String idempotencyKey) {
        PermissionChecker.requireUser();
        Long userId = getCurrentUserIdOrThrow();
        String normalizedKey = idempotencyKey.trim();
        Long orderId = null;

        try {
            OrderIdempotency existing = orderIdempotencyMapper.selectByUserIdAndKey(userId, normalizedKey);
            if (existing != null) {
                orderId = handleExistingIdempotency(existing, userId, orderSubmitDTO);
                businessMetrics.recordOrderSubmit("duplicate", "idempotent_replay");
                log.info("Order submit outcome traceId={} result=idempotent_duplicate userId={} orderId={} idempotencyKey={}",
                        traceId(), userId, orderId, normalizedKey);
                return orderId;
            }

            List<ShoppingCart> cartItems = shoppingCartMapper.selectByUserId(userId);
            if (cartItems.isEmpty()) {
                throw new BusinessException("购物车为空，不能下单");
            }
            List<ShoppingCart> refreshedCartItems = refreshCartItems(cartItems);
            String requestHash = buildRequestHash(userId, orderSubmitDTO.getRemark(), refreshedCartItems);

            OrderIdempotency orderIdempotency = buildProcessingIdempotency(userId, normalizedKey, requestHash);
            try {
                orderIdempotencyMapper.insert(orderIdempotency);
            } catch (DuplicateKeyException e) {
                OrderIdempotency duplicate = orderIdempotencyMapper.selectByUserIdAndKey(userId, normalizedKey);
                if (duplicate == null) {
                    throw e;
                }
                orderId = handleExistingIdempotency(duplicate, requestHash);
                businessMetrics.recordOrderSubmit("duplicate", "concurrent_replay");
                log.info("Order submit outcome traceId={} result=idempotent_duplicate userId={} orderId={} idempotencyKey={}",
                        traceId(), userId, orderId, normalizedKey);
                return orderId;
            }

            BigDecimal totalAmount = refreshedCartItems.stream()
                    .map(item -> item.getDishPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Orders orders = new Orders();
            orders.setNumber(OrderNumberUtil.generateOrderNumber());
            orders.setUserId(userId);
            orders.setStatus(OrderStatus.PENDING_PAYMENT.getCode());
            orders.setAmount(totalAmount);
            orders.setRemark(orderSubmitDTO.getRemark());
            orders.setOrderTime(LocalDateTime.now());
            ordersMapper.insert(orders);
            orderId = orders.getId();

            dishStockService.lockStock(orders.getId(), aggregateCartQuantities(refreshedCartItems), userId);

            List<OrderDetail> details = refreshedCartItems.stream()
                    .map(item -> toOrderDetail(orders.getId(), item))
                    .toList();
            orderDetailMapper.insertBatch(details);

            shoppingCartMapper.deleteByUserId(userId);
            orderTimeoutOutboxService.createPendingForOrder(orders.getId());
            int rows = orderIdempotencyMapper.markSucceeded(
                    orderIdempotency.getId(),
                    orders.getId(),
                    OrderIdempotencyStatus.SUCCEEDED.getCode(),
                    LocalDateTime.now());
            if (rows == 0) {
                throw new BusinessException("下单幂等记录状态更新失败，请重试");
            }
            orderStatusHistoryService.recordChange(
                    orders,
                    null,
                    OrderStatus.PENDING_PAYMENT,
                    OrderStatusChangeOperation.ORDER_SUBMIT,
                    userId,
                    Role.USER,
                    null);
            businessMetrics.recordOrderSubmit("success", "created");
            log.info("Order submit outcome traceId={} result=success userId={} orderId={} idempotencyKey={}",
                    traceId(), userId, orders.getId(), normalizedKey);
            return orders.getId();
        } catch (BusinessException e) {
            String result = e.getCode() == 409 ? "conflict" : "fail";
            String reason = orderSubmitReason(e);
            businessMetrics.recordOrderSubmit(result, reason);
            log.warn("Order submit outcome traceId={} result={} reason={} userId={} orderId={} idempotencyKey={}",
                    traceId(), result, reason, userId, orderId, normalizedKey);
            throw e;
        } catch (Exception e) {
            businessMetrics.recordOrderSubmit("fail", "unexpected");
            log.error("Order submit outcome traceId={} result=fail reason=unexpected userId={} orderId={} idempotencyKey={}",
                    traceId(), userId, orderId, normalizedKey, e);
            throw e;
        }
    }

    @Override
    public OrderDetailVO getDetail(Long id) {
        Orders orders = getVisibleOrderOrThrow(id);
        List<OrderItemVO> items = orderDetailMapper.selectByOrderId(id).stream()
                .map(this::toOrderItemVO)
                .toList();
        return toOrderDetailVO(orders, items);
    }

    @Override
    public List<OrderStatusHistoryVO> getStatusHistory(Long id) {
        getVisibleOrderOrThrow(id);
        return orderStatusHistoryService.listByOrderId(id);
    }

    @Override
    public PageResult<OrderVO> page(OrderPageQueryDTO orderPageQueryDTO) {
        Long userId = getQueryUserId();
        List<Integer> visibleStatusCodes = getVisibleListStatusCodes();
        if (isRequestedStatusOutsideScope(orderPageQueryDTO, visibleStatusCodes)) {
            return new PageResult<>(0L, List.of());
        }
        int offset = (orderPageQueryDTO.getPage() - 1) * orderPageQueryDTO.getPageSize();
        long total = ordersMapper.countPage(userId, visibleStatusCodes, orderPageQueryDTO);
        List<OrderVO> records = ordersMapper.selectPage(userId, visibleStatusCodes, orderPageQueryDTO, offset, orderPageQueryDTO.getPageSize())
                .stream()
                .map(this::toOrderVO)
                .toList();
        return new PageResult<>(total, records);
    }

    @Override
    @Transactional
    public OrderPayVO pay(Long id) {
        PermissionChecker.requireUser();
        return executeWithOrderStatusLock(id, () -> {
            Orders orders = getOwnOrderOrThrow(id);
            OrderStatus oldStatus = OrderStatus.fromCode(orders.getStatus());
            if (oldStatus != OrderStatus.PENDING_PAYMENT) {
                throw new BusinessException("只有待支付订单才能发起支付");
            }

            PaymentRecord paymentRecord = paymentRecordMapper.selectLatestMockByOrderId(id, PAYMENT_CHANNEL_MOCK);
            if (paymentRecord == null || PaymentStatus.FAILED.getCode() == paymentRecord.getStatus()) {
                paymentRecord = buildMockPaymentRecord(orders, LocalDateTime.now());
                paymentRecordMapper.insert(paymentRecord);
            } else if (PaymentStatus.PAYING.getCode() != paymentRecord.getStatus()) {
                throw new BusinessException("当前支付流水状态不允许重新发起支付");
            }
            log.info("Mock payment initiation traceId={} result=success userId={} orderId={} tradeNo={}",
                    traceId(), orders.getUserId(), orders.getId(), paymentRecord.getTradeNo());

            return OrderPayVO.builder()
                    .orderId(orders.getId())
                    .orderNumber(orders.getNumber())
                    .orderStatus(orders.getStatus())
                    .amount(orders.getAmount())
                    .tradeNo(paymentRecord.getTradeNo())
                    .payStatus(PaymentStatus.PAYING.getCode())
                    .requestTime(paymentRecord.getRequestTime())
                    .build();
        });
    }

    @Override
    @Transactional
    public void cancel(Long id) {
        PermissionChecker.requireUser();
        executeWithOrderStatusLock(id, () -> {
            Orders orders = getOwnOrderOrThrow(id);
            OrderStatus oldStatus = requireTransition(orders, OrderStatus.CANCELLED, "只有待支付订单才能取消");
            int rows = ordersMapper.updateToCancelledById(
                    id, LocalDateTime.now(), oldStatus.getCode(), OrderStatus.CANCELLED.getCode());
            if (rows == 0) {
                throw new BusinessException("订单状态已变化，请刷新后重试");
            }
            dishStockService.releaseLockedStock(
                    id,
                    aggregateOrderDetailQuantities(orderDetailMapper.selectByOrderId(id)),
                    getCurrentUserIdOrThrow());
            closeCurrentPayingMockPayment(id);
            orderStatusHistoryService.recordChange(
                    orders,
                    oldStatus.getCode(),
                    OrderStatus.CANCELLED,
                    OrderStatusChangeOperation.USER_CANCEL,
                    getCurrentUserIdOrThrow(),
                    Role.USER,
                    null);
            log.info("Order cancel outcome traceId={} result=success source=manual userId={} orderId={}",
                    traceId(), BaseContext.getCurrentUserId(), id);
        });
    }

    @Override
    @Transactional
    public void accept(Long id) {
        PermissionChecker.requireMerchantOrAdmin();
        executeStatusOnlyTransition(
                id,
                OrderStatus.ACCEPTED,
                OrderStatusChangeOperation.MERCHANT_ACCEPT,
                this::getOrderOrThrow,
                "只有已支付订单才能接单");
    }

    @Override
    @Transactional
    public void startDelivery(Long id) {
        PermissionChecker.requireDeliveryOrAdmin();
        executeStatusOnlyTransition(
                id,
                OrderStatus.DELIVERING,
                OrderStatusChangeOperation.DELIVERY_START,
                this::getOrderOrThrow,
                "只有已接单订单才能开始配送");
    }

    @Override
    @Transactional
    public void complete(Long id) {
        PermissionChecker.requireDeliveryOrAdmin();
        executeTimestampTransition(
                id,
                OrderStatus.COMPLETED,
                OrderStatusChangeOperation.DELIVERY_COMPLETE,
                this::getOrderOrThrow,
                "只有配送中订单才能完成",
                (orderId, oldStatus) -> ordersMapper.updateToCompletedById(
                        orderId, LocalDateTime.now(), oldStatus.getCode(), OrderStatus.COMPLETED.getCode()));
    }

    @Override
    @Transactional
    public void startRefund(Long id) {
        PermissionChecker.requireAdmin();
        rejectLegacyRefundWhenRequestExists(id);
        executeStatusOnlyTransition(
                id,
                OrderStatus.REFUNDING,
                OrderStatusChangeOperation.INTERNAL_REFUND_START,
                this::getOrderOrThrow,
                "只有已支付或已接单订单才能发起内部模拟退款");
    }

    @Override
    @Transactional
    public void completeRefund(Long id) {
        PermissionChecker.requireAdmin();
        rejectLegacyRefundWhenRequestExists(id);
        executeStatusOnlyTransition(
                id,
                OrderStatus.REFUNDED,
                OrderStatusChangeOperation.INTERNAL_REFUND_COMPLETE,
                this::getOrderOrThrow,
                "只有模拟退款中的订单才能完成内部模拟退款");
    }

    @Override
    @Transactional
    public void timeoutCancel(Long id, String messageId) {
        executeWithOrderStatusLock(id, () -> {
            Orders orders = ordersMapper.selectById(id);
            if (orders == null) {
                businessMetrics.recordTimeoutCancel("noop");
                log.info("Order cancel outcome traceId={} result=noop source=timeout operator=system orderId={} messageId={} reason=not_found",
                        traceId(), id, messageId);
                return;
            }

            OrderStatus currentStatus = OrderStatus.fromCode(orders.getStatus());
            if (currentStatus != OrderStatus.PENDING_PAYMENT) {
                businessMetrics.recordTimeoutCancel("noop");
                log.info("Order cancel outcome traceId={} result=noop source=timeout operator=system orderId={} messageId={} reason=status_{}",
                        traceId(), id, messageId, currentStatus.name().toLowerCase());
                return;
            }

            int rows = ordersMapper.updateToCancelledById(
                    id,
                    LocalDateTime.now(),
                    OrderStatus.PENDING_PAYMENT.getCode(),
                    OrderStatus.CANCELLED.getCode());
            if (rows == 0) {
                businessMetrics.recordTimeoutCancel("noop");
                log.info("Order cancel outcome traceId={} result=noop source=timeout operator=system orderId={} messageId={} reason=state_changed",
                        traceId(), id, messageId);
                return;
            }

            Long systemOperatorId = userMapper.selectIdByUsername(Constants.SYSTEM_TIMEOUT_USERNAME);
            dishStockService.releaseLockedStock(
                    id,
                    aggregateOrderDetailQuantities(orderDetailMapper.selectByOrderId(id)),
                    systemOperatorId,
                    TIMEOUT_RELEASE_REMARK);
            closeCurrentPayingMockPayment(id);
            orderStatusHistoryService.recordSystemChange(
                    orders,
                    OrderStatus.PENDING_PAYMENT.getCode(),
                    OrderStatus.CANCELLED,
                    OrderStatusChangeOperation.TIMEOUT_CANCEL,
                    null);
            businessMetrics.recordTimeoutCancel("success");
            log.info("Order cancel outcome traceId={} result=success source=timeout operator=system orderId={} messageId={}",
                    traceId(), id, messageId);
        });
    }

    private Orders getOwnOrderOrThrow(Long id) {
        Long userId = getCurrentUserIdOrThrow();
        Orders orders = ordersMapper.selectById(id);
        if (orders == null || !userId.equals(orders.getUserId())) {
            throw new BusinessException(404, "订单不存在");
        }
        return orders;
    }

    private void rejectLegacyRefundWhenRequestExists(Long orderId) {
        if (refundRequestMapper.selectByOrderId(orderId) != null) {
            throw new BusinessException("该订单已有退款申请，请使用 /refund/... 流程处理");
        }
    }

    private Orders getVisibleOrderOrThrow(Long id) {
        Orders orders = getOrderOrThrow(id);
        Role role = PermissionChecker.currentRoleOrThrow();
        OrderStatus status = OrderStatus.fromCode(orders.getStatus());
        if (role == Role.ADMIN) {
            return orders;
        }
        if (role == Role.USER) {
            Long userId = getCurrentUserIdOrThrow();
            if (userId.equals(orders.getUserId())) {
                return orders;
            }
            throw new BusinessException(404, "订单不存在");
        }
        if (role == Role.MERCHANT && merchantDetailStatuses().contains(status)) {
            return orders;
        }
        if (role == Role.DELIVERY && deliveryDetailStatuses().contains(status)) {
            return orders;
        }
        PermissionChecker.throwPermissionDenied();
        return null;
    }

    private Orders getOrderOrThrow(Long id) {
        Orders orders = ordersMapper.selectById(id);
        if (orders == null) {
            throw new BusinessException(404, "订单不存在");
        }
        return orders;
    }

    private Long getQueryUserId() {
        Role role = PermissionChecker.currentRoleOrThrow();
        if (role == Role.USER) {
            return getCurrentUserIdOrThrow();
        }
        if (role == Role.MERCHANT || role == Role.DELIVERY || role == Role.ADMIN) {
            return null;
        }
        PermissionChecker.throwPermissionDenied();
        return null;
    }

    private List<Integer> getVisibleListStatusCodes() {
        Role role = PermissionChecker.currentRoleOrThrow();
        if (role == Role.MERCHANT) {
            return statusCodes(OrderStatus.PAID, OrderStatus.ACCEPTED);
        }
        if (role == Role.DELIVERY) {
            return statusCodes(OrderStatus.ACCEPTED, OrderStatus.DELIVERING);
        }
        if (role == Role.USER || role == Role.ADMIN) {
            return null;
        }
        PermissionChecker.throwPermissionDenied();
        return List.of();
    }

    private boolean isRequestedStatusOutsideScope(OrderPageQueryDTO query, List<Integer> visibleStatusCodes) {
        return query.getStatus() != null
                && visibleStatusCodes != null
                && !visibleStatusCodes.contains(query.getStatus());
    }

    private List<Integer> statusCodes(OrderStatus... statuses) {
        return java.util.Arrays.stream(statuses)
                .map(OrderStatus::getCode)
                .toList();
    }

    private Set<OrderStatus> merchantDetailStatuses() {
        return Set.of(
                OrderStatus.PAID,
                OrderStatus.ACCEPTED,
                OrderStatus.DELIVERING,
                OrderStatus.COMPLETED,
                OrderStatus.REFUNDING,
                OrderStatus.REFUNDED);
    }

    private Set<OrderStatus> deliveryDetailStatuses() {
        return Set.of(OrderStatus.ACCEPTED, OrderStatus.DELIVERING, OrderStatus.COMPLETED);
    }

    private Long getCurrentUserIdOrThrow() {
        Long userId = BaseContext.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        return userId;
    }

    private <T> T executeWithOrderStatusLock(Long orderId, Supplier<T> action) {
        String lockKey = Constants.ORDER_STATUS_LOCK_KEY_PREFIX + orderId;
        String lockValue = UUID.randomUUID().toString();
        boolean locked = redisDistributedLock.tryLock(lockKey, lockValue, ORDER_STATUS_LOCK_TTL);
        if (!locked) {
            throw new BusinessException(Constants.ORDER_STATUS_LOCK_FAILED_MESSAGE);
        }

        try {
            return action.get();
        } finally {
            releaseOrderStatusLock(lockKey, lockValue);
        }
    }

    private void executeWithOrderStatusLock(Long orderId, Runnable action) {
        executeWithOrderStatusLock(orderId, () -> {
            action.run();
            return null;
        });
    }

    private void executeStatusOnlyTransition(Long id,
                                             OrderStatus targetStatus,
                                             OrderStatusChangeOperation operation,
                                             Function<Long, Orders> orderLoader,
                                             String illegalMessage) {
        executeTimestampTransition(
                id,
                targetStatus,
                operation,
                orderLoader,
                illegalMessage,
                (orderId, oldStatus) -> ordersMapper.updateStatusById(
                        orderId, oldStatus.getCode(), targetStatus.getCode()));
    }

    private void executeTimestampTransition(Long id,
                                            OrderStatus targetStatus,
                                            OrderStatusChangeOperation operation,
                                            Function<Long, Orders> orderLoader,
                                            String illegalMessage,
                                            BiFunction<Long, OrderStatus, Integer> updater) {
        executeWithOrderStatusLock(id, () -> {
            Orders orders = orderLoader.apply(id);
            OrderStatus oldStatus = requireTransition(orders, targetStatus, illegalMessage);
            int rows = updater.apply(id, oldStatus);
            if (rows == 0) {
                throw new BusinessException("订单状态已变化，请刷新后重试");
            }
            orderStatusHistoryService.recordChange(
                    orders,
                    oldStatus.getCode(),
                    targetStatus,
                    operation,
                    getCurrentUserIdOrThrow(),
                    PermissionChecker.currentRoleOrThrow(),
                    null);
        });
    }

    private OrderStatus requireTransition(Orders orders, OrderStatus targetStatus, String illegalMessage) {
        OrderStatus oldStatus = OrderStatus.fromCode(orders.getStatus());
        if (!oldStatus.canTransitionTo(targetStatus)) {
            throw new BusinessException(illegalMessage);
        }
        return oldStatus;
    }

    private void releaseOrderStatusLock(String lockKey, String lockValue) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            redisDistributedLock.unlock(lockKey, lockValue);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                redisDistributedLock.unlock(lockKey, lockValue);
            }
        });
    }

    private PaymentRecord buildMockPaymentRecord(Orders orders, LocalDateTime requestTime) {
        PaymentRecord paymentRecord = new PaymentRecord();
        paymentRecord.setOrderId(orders.getId());
        paymentRecord.setOrderNumber(orders.getNumber());
        paymentRecord.setUserId(orders.getUserId());
        paymentRecord.setAmount(orders.getAmount());
        paymentRecord.setPayChannel(PAYMENT_CHANNEL_MOCK);
        paymentRecord.setTradeNo(PaymentTradeNoUtil.generateTradeNo());
        paymentRecord.setStatus(PaymentStatus.PAYING.getCode());
        paymentRecord.setRequestTime(requestTime);
        paymentRecord.setCreateTime(requestTime);
        paymentRecord.setUpdateTime(requestTime);
        return paymentRecord;
    }

    private void closeCurrentPayingMockPayment(Long orderId) {
        paymentRecordMapper.closeCurrentPayingMockByOrderId(
                orderId,
                PAYMENT_CHANNEL_MOCK,
                PaymentStatus.PAYING.getCode(),
                PaymentStatus.CLOSED.getCode(),
                LocalDateTime.now());
    }

    private List<ShoppingCart> refreshCartItems(List<ShoppingCart> cartItems) {
        return cartItems.stream()
                .map(this::refreshCartItem)
                .toList();
    }

    private Long handleExistingIdempotency(OrderIdempotency existing,
                                           Long userId,
                                           OrderSubmitDTO orderSubmitDTO) {
        List<ShoppingCart> cartItems = shoppingCartMapper.selectByUserId(userId);
        if (cartItems.isEmpty()) {
            return handleExistingIdempotencyWithClearedCart(existing, userId, orderSubmitDTO);
        }
        List<ShoppingCart> refreshedCartItems = refreshCartItems(cartItems);
        String requestHash = buildRequestHash(userId, orderSubmitDTO.getRemark(), refreshedCartItems);
        return handleExistingIdempotency(existing, requestHash);
    }

    private Long handleExistingIdempotencyWithClearedCart(OrderIdempotency existing,
                                                          Long userId,
                                                          OrderSubmitDTO orderSubmitDTO) {
        if (!Integer.valueOf(OrderIdempotencyStatus.SUCCEEDED.getCode()).equals(existing.getStatus())
                || existing.getOrderId() == null) {
            return handleExistingIdempotency(existing, null);
        }
        List<ShoppingCart> submittedItems = orderDetailMapper.selectByOrderId(existing.getOrderId()).stream()
                .map(this::toSubmittedCartItem)
                .toList();
        String requestHash = buildRequestHash(userId, orderSubmitDTO.getRemark(), submittedItems);
        return handleExistingIdempotency(existing, requestHash);
    }

    private Long handleExistingIdempotency(OrderIdempotency existing, String requestHash) {
        if (requestHash != null && !requestHash.equals(existing.getRequestHash())) {
            throw new BusinessException(409, "Idempotency-Key 已被不同下单请求使用");
        }

        OrderIdempotencyStatus status = OrderIdempotencyStatus.fromCode(existing.getStatus());
        if (status == OrderIdempotencyStatus.SUCCEEDED && existing.getOrderId() != null) {
            return existing.getOrderId();
        }
        if (status == OrderIdempotencyStatus.PROCESSING) {
            throw new BusinessException(409, "订单正在处理中，请稍后重试");
        }
        throw new BusinessException(409, "上一次下单请求未成功，请使用新的 Idempotency-Key");
    }

    private String orderSubmitReason(BusinessException e) {
        if (e.getCode() == 409) {
            return "idempotency_conflict";
        }
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("库存")) {
            return "stock";
        }
        if (message.contains("购物车")) {
            return "cart";
        }
        return "business";
    }

    private String traceId() {
        return org.slf4j.MDC.get(TraceContext.TRACE_ID);
    }

    private OrderIdempotency buildProcessingIdempotency(Long userId, String idempotencyKey, String requestHash) {
        LocalDateTime now = LocalDateTime.now();
        OrderIdempotency orderIdempotency = new OrderIdempotency();
        orderIdempotency.setUserId(userId);
        orderIdempotency.setIdempotencyKey(idempotencyKey);
        orderIdempotency.setRequestHash(requestHash);
        orderIdempotency.setStatus(OrderIdempotencyStatus.PROCESSING.getCode());
        orderIdempotency.setCreateTime(now);
        orderIdempotency.setUpdateTime(now);
        return orderIdempotency;
    }

    String buildRequestHash(Long userId, String remark, List<ShoppingCart> cartItems) {
        StringBuilder content = new StringBuilder();
        content.append("userId=").append(userId).append('\n');
        content.append("remark=").append(remark == null ? "" : remark).append('\n');
        cartItems.stream()
                .sorted(Comparator.comparing(ShoppingCart::getDishId))
                .forEach(item -> content
                        .append("dishId=").append(item.getDishId())
                        .append(",quantity=").append(item.getQuantity())
                        .append(",dishPrice=").append(normalizeAmount(item.getDishPrice()))
                        .append('\n'));
        return sha256Hex(content.toString());
    }

    private String normalizeAmount(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }

    private String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }

    private ShoppingCart toSubmittedCartItem(OrderDetail orderDetail) {
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setDishId(orderDetail.getDishId());
        shoppingCart.setDishName(orderDetail.getDishName());
        shoppingCart.setDishPrice(orderDetail.getDishPrice());
        shoppingCart.setQuantity(orderDetail.getQuantity());
        return shoppingCart;
    }

    private ShoppingCart refreshCartItem(ShoppingCart cartItem) {
        Dish dish = dishMapper.selectById(cartItem.getDishId());
        if (dish == null) {
            throw new BusinessException("购物车中存在已删除商品，请先移出购物车");
        }
        if (!Integer.valueOf(1).equals(dish.getStatus())) {
            throw new BusinessException("购物车中存在已下架商品，请先移出购物车");
        }
        cartItem.setDishName(dish.getName());
        cartItem.setDishPrice(dish.getPrice());
        return cartItem;
    }

    private Map<Long, Integer> aggregateCartQuantities(List<ShoppingCart> cartItems) {
        return cartItems.stream()
                .collect(Collectors.groupingBy(
                        ShoppingCart::getDishId,
                        Collectors.summingInt(ShoppingCart::getQuantity)));
    }

    private Map<Long, Integer> aggregateOrderDetailQuantities(List<OrderDetail> details) {
        return details.stream()
                .collect(Collectors.groupingBy(
                        OrderDetail::getDishId,
                        Collectors.summingInt(OrderDetail::getQuantity)));
    }

    private OrderDetail toOrderDetail(Long orderId, ShoppingCart shoppingCart) {
        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setOrderId(orderId);
        orderDetail.setDishId(shoppingCart.getDishId());
        orderDetail.setDishName(shoppingCart.getDishName());
        orderDetail.setDishPrice(shoppingCart.getDishPrice());
        orderDetail.setQuantity(shoppingCart.getQuantity());
        orderDetail.setAmount(shoppingCart.getDishPrice().multiply(BigDecimal.valueOf(shoppingCart.getQuantity())));
        return orderDetail;
    }

    private OrderItemVO toOrderItemVO(OrderDetail orderDetail) {
        return OrderItemVO.builder()
                .dishId(orderDetail.getDishId())
                .dishName(orderDetail.getDishName())
                .dishPrice(orderDetail.getDishPrice())
                .quantity(orderDetail.getQuantity())
                .amount(orderDetail.getAmount())
                .build();
    }

    private OrderDetailVO toOrderDetailVO(Orders orders, List<OrderItemVO> items) {
        return OrderDetailVO.builder()
                .id(orders.getId())
                .number(orders.getNumber())
                .status(orders.getStatus())
                .amount(orders.getAmount())
                .remark(orders.getRemark())
                .orderTime(orders.getOrderTime())
                .payTime(orders.getPayTime())
                .cancelTime(orders.getCancelTime())
                .completeTime(orders.getCompleteTime())
                .items(items)
                .build();
    }

    private OrderVO toOrderVO(Orders orders) {
        return OrderVO.builder()
                .id(orders.getId())
                .number(orders.getNumber())
                .status(orders.getStatus())
                .amount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();
    }
}
