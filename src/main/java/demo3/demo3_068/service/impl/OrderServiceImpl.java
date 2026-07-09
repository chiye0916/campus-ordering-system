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
import demo3.demo3_068.mapper.ShoppingCartMapper;
import demo3.demo3_068.mapper.UserMapper;
import demo3.demo3_068.model.OrderIdempotencyStatus;
import demo3.demo3_068.model.OrderStatus;
import demo3.demo3_068.service.DishStockService;
import demo3.demo3_068.service.OrderService;
import demo3.demo3_068.service.OrderTimeoutOutboxService;
import demo3.demo3_068.utils.OrderNumberUtil;
import demo3.demo3_068.utils.PaymentTradeNoUtil;
import demo3.demo3_068.vo.OrderDetailVO;
import demo3.demo3_068.vo.OrderItemVO;
import demo3.demo3_068.vo.OrderPayVO;
import demo3.demo3_068.vo.OrderVO;
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
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private static final int PAYMENT_STATUS_PAYING = 1;
    private static final int PAYMENT_STATUS_SUCCESS = 2;
    private static final String PAYMENT_CHANNEL_MOCK = "MOCK";
    private static final String TIMEOUT_RELEASE_REMARK = "订单超时自动取消释放库存";
    private static final Duration ORDER_STATUS_LOCK_TTL = Duration.ofSeconds(30);

    private final OrdersMapper ordersMapper;
    private final OrderDetailMapper orderDetailMapper;
    private final OrderIdempotencyMapper orderIdempotencyMapper;
    private final ShoppingCartMapper shoppingCartMapper;
    private final DishMapper dishMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final UserMapper userMapper;
    private final RedisDistributedLock redisDistributedLock;
    private final DishStockService dishStockService;
    private final OrderTimeoutOutboxService orderTimeoutOutboxService;

    public OrderServiceImpl(OrdersMapper ordersMapper,
                            OrderDetailMapper orderDetailMapper,
                            OrderIdempotencyMapper orderIdempotencyMapper,
                            ShoppingCartMapper shoppingCartMapper,
                            DishMapper dishMapper,
                            PaymentRecordMapper paymentRecordMapper,
                            UserMapper userMapper,
                            RedisDistributedLock redisDistributedLock,
                            DishStockService dishStockService,
                            OrderTimeoutOutboxService orderTimeoutOutboxService) {
        this.ordersMapper = ordersMapper;
        this.orderDetailMapper = orderDetailMapper;
        this.orderIdempotencyMapper = orderIdempotencyMapper;
        this.shoppingCartMapper = shoppingCartMapper;
        this.dishMapper = dishMapper;
        this.paymentRecordMapper = paymentRecordMapper;
        this.userMapper = userMapper;
        this.redisDistributedLock = redisDistributedLock;
        this.dishStockService = dishStockService;
        this.orderTimeoutOutboxService = orderTimeoutOutboxService;
    }

    @Override
    @Transactional
    public Long submit(OrderSubmitDTO orderSubmitDTO, String idempotencyKey) {
        Long userId = getCurrentUserIdOrThrow();
        String normalizedKey = idempotencyKey.trim();

        OrderIdempotency existing = orderIdempotencyMapper.selectByUserIdAndKey(userId, normalizedKey);
        if (existing != null) {
            return handleExistingIdempotency(existing, userId, orderSubmitDTO);
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
            return handleExistingIdempotency(duplicate, requestHash);
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
        return orders.getId();
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
    public PageResult<OrderVO> page(OrderPageQueryDTO orderPageQueryDTO) {
        Long userId = getQueryUserId();
        int offset = (orderPageQueryDTO.getPage() - 1) * orderPageQueryDTO.getPageSize();
        long total = ordersMapper.countPage(userId, orderPageQueryDTO);
        List<OrderVO> records = ordersMapper.selectPage(userId, orderPageQueryDTO, offset, orderPageQueryDTO.getPageSize())
                .stream()
                .map(this::toOrderVO)
                .toList();
        return new PageResult<>(total, records);
    }

    @Override
    @Transactional
    public OrderPayVO pay(Long id) {
        return executeWithOrderStatusLock(id, () -> {
            Orders orders = getOwnOrderOrThrow(id);
            OrderStatus oldStatus = requireTransition(orders, OrderStatus.PAID, "只有待支付订单才能支付");

            LocalDateTime payTime = LocalDateTime.now();
            PaymentRecord paymentRecord = buildMockPaymentRecord(orders, payTime);
            paymentRecordMapper.insert(paymentRecord);

            int rows = ordersMapper.updateToPaidById(id, payTime, oldStatus.getCode(), OrderStatus.PAID.getCode());
            if (rows == 0) {
                throw new BusinessException("订单状态已变化，请刷新后重试");
            }

            dishStockService.confirmLockedStock(
                    id,
                    aggregateOrderDetailQuantities(orderDetailMapper.selectByOrderId(id)),
                    getCurrentUserIdOrThrow());

            int paymentRows = paymentRecordMapper.updateStatusToSuccessById(
                    paymentRecord.getId(), payTime, PAYMENT_STATUS_PAYING, PAYMENT_STATUS_SUCCESS);
            if (paymentRows == 0) {
                throw new BusinessException("支付流水状态已变化，请刷新后重试");
            }

            return OrderPayVO.builder()
                    .orderId(orders.getId())
                    .orderNumber(orders.getNumber())
                    .status(OrderStatus.PAID.getCode())
                    .amount(orders.getAmount())
                    .payTime(payTime)
                    .build();
        });
    }

    @Override
    @Transactional
    public void cancel(Long id) {
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
        });
    }

    @Override
    @Transactional
    public void accept(Long id) {
        requireAdminSuperOperator();
        executeStatusOnlyTransition(id, OrderStatus.ACCEPTED, this::getOrderOrThrow, "只有已支付订单才能接单");
    }

    @Override
    @Transactional
    public void startDelivery(Long id) {
        requireAdminSuperOperator();
        executeStatusOnlyTransition(id, OrderStatus.DELIVERING, this::getOrderOrThrow, "只有已接单订单才能开始配送");
    }

    @Override
    @Transactional
    public void complete(Long id) {
        requireAdminSuperOperator();
        executeTimestampTransition(
                id,
                OrderStatus.COMPLETED,
                this::getOrderOrThrow,
                "只有配送中订单才能完成",
                (orderId, oldStatus) -> ordersMapper.updateToCompletedById(
                        orderId, LocalDateTime.now(), oldStatus.getCode(), OrderStatus.COMPLETED.getCode()));
    }

    @Override
    @Transactional
    public void startRefund(Long id) {
        requireAdminSuperOperator();
        executeStatusOnlyTransition(
                id,
                OrderStatus.REFUNDING,
                this::getOrderOrThrow,
                "只有已支付或已接单订单才能发起内部模拟退款");
    }

    @Override
    @Transactional
    public void completeRefund(Long id) {
        requireAdminSuperOperator();
        executeStatusOnlyTransition(
                id,
                OrderStatus.REFUNDED,
                this::getOrderOrThrow,
                "只有模拟退款中的订单才能完成内部模拟退款");
    }

    @Override
    @Transactional
    public void timeoutCancel(Long id, String messageId) {
        executeWithOrderStatusLock(id, () -> {
            Orders orders = ordersMapper.selectById(id);
            if (orders == null) {
                return;
            }

            OrderStatus currentStatus = OrderStatus.fromCode(orders.getStatus());
            if (currentStatus != OrderStatus.PENDING_PAYMENT) {
                return;
            }

            int rows = ordersMapper.updateToCancelledById(
                    id,
                    LocalDateTime.now(),
                    OrderStatus.PENDING_PAYMENT.getCode(),
                    OrderStatus.CANCELLED.getCode());
            if (rows == 0) {
                return;
            }

            Long systemOperatorId = userMapper.selectIdByUsername(Constants.SYSTEM_TIMEOUT_USERNAME);
            if (systemOperatorId == null) {
                throw new BusinessException("订单超时系统用户不存在");
            }
            dishStockService.releaseLockedStock(
                    id,
                    aggregateOrderDetailQuantities(orderDetailMapper.selectByOrderId(id)),
                    systemOperatorId,
                    TIMEOUT_RELEASE_REMARK);
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

    private Orders getVisibleOrderOrThrow(Long id) {
        Orders orders = getOrderOrThrow(id);
        if (isAdmin()) {
            return orders;
        }
        Long userId = getCurrentUserIdOrThrow();
        if (!userId.equals(orders.getUserId())) {
            throw new BusinessException(404, "订单不存在");
        }
        return orders;
    }

    private Orders getOrderOrThrow(Long id) {
        Orders orders = ordersMapper.selectById(id);
        if (orders == null) {
            throw new BusinessException(404, "订单不存在");
        }
        return orders;
    }

    private Long getQueryUserId() {
        Long userId = getCurrentUserIdOrThrow();
        return isAdmin() ? null : userId;
    }

    private boolean isAdmin() {
        return Constants.ADMIN_USER_ROLE.equals(BaseContext.getCurrentUserRole());
    }

    private void requireAdminSuperOperator() {
        // ADMIN is the first-version maximum-permission demo operator, not the final merchant role.
        PermissionChecker.requireAdmin();
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
                                             Function<Long, Orders> orderLoader,
                                             String illegalMessage) {
        executeTimestampTransition(
                id,
                targetStatus,
                orderLoader,
                illegalMessage,
                (orderId, oldStatus) -> ordersMapper.updateStatusById(
                        orderId, oldStatus.getCode(), targetStatus.getCode()));
    }

    private void executeTimestampTransition(Long id,
                                            OrderStatus targetStatus,
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
        paymentRecord.setStatus(PAYMENT_STATUS_PAYING);
        paymentRecord.setRequestTime(requestTime);
        paymentRecord.setCreateTime(requestTime);
        paymentRecord.setUpdateTime(requestTime);
        return paymentRecord;
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
