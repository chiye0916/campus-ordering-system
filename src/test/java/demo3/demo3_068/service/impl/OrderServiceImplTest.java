package demo3.demo3_068.service.impl;

import demo3.demo3_068.common.BaseContext;
import demo3.demo3_068.dto.OrderSubmitDTO;
import demo3.demo3_068.entity.Dish;
import demo3.demo3_068.entity.OrderIdempotency;
import demo3.demo3_068.entity.OrderDetail;
import demo3.demo3_068.entity.Orders;
import demo3.demo3_068.entity.PaymentRecord;
import demo3.demo3_068.entity.ShoppingCart;
import demo3.demo3_068.exception.BusinessException;
import demo3.demo3_068.mapper.DishMapper;
import demo3.demo3_068.mapper.OrderDetailMapper;
import demo3.demo3_068.mapper.OrderIdempotencyMapper;
import demo3.demo3_068.mapper.OrdersMapper;
import demo3.demo3_068.mapper.PaymentRecordMapper;
import demo3.demo3_068.mapper.ShoppingCartMapper;
import demo3.demo3_068.mapper.UserMapper;
import demo3.demo3_068.model.OrderIdempotencyStatus;
import demo3.demo3_068.model.OrderStatus;
import demo3.demo3_068.common.RedisDistributedLock;
import demo3.demo3_068.model.PaymentStatus;
import demo3.demo3_068.observability.BusinessMetrics;
import demo3.demo3_068.service.DishStockService;
import demo3.demo3_068.service.OrderTimeoutOutboxService;
import demo3.demo3_068.vo.OrderPayVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrdersMapper ordersMapper;
    @Mock
    private OrderDetailMapper orderDetailMapper;
    @Mock
    private OrderIdempotencyMapper orderIdempotencyMapper;
    @Mock
    private ShoppingCartMapper shoppingCartMapper;
    @Mock
    private DishMapper dishMapper;
    @Mock
    private PaymentRecordMapper paymentRecordMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private RedisDistributedLock redisDistributedLock;
    @Mock
    private DishStockService dishStockService;
    @Mock
    private OrderTimeoutOutboxService orderTimeoutOutboxService;
    @Mock
    private BusinessMetrics businessMetrics;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(
                ordersMapper,
                orderDetailMapper,
                orderIdempotencyMapper,
                shoppingCartMapper,
                dishMapper,
                paymentRecordMapper,
                userMapper,
                redisDistributedLock,
                dishStockService,
                orderTimeoutOutboxService,
                businessMetrics);
        BaseContext.setCurrentUserId(7L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void requestHashIsStableForCartItemOrderAndPriceScale() {
        String first = orderService.buildRequestHash(7L, "少辣", List.of(
                cartItem(2L, "20.00", 1),
                cartItem(1L, "12.50", 2)));

        String second = orderService.buildRequestHash(7L, "少辣", List.of(
                cartItem(1L, "12.500", 2),
                cartItem(2L, "20.0", 1)));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void requestHashChangesWhenEffectiveSubmitContentChanges() {
        String first = orderService.buildRequestHash(7L, "少辣", List.of(cartItem(1L, "12.50", 2)));
        String second = orderService.buildRequestHash(7L, "不要辣", List.of(cartItem(1L, "12.50", 2)));
        String third = orderService.buildRequestHash(7L, "少辣", List.of(cartItem(1L, "12.50", 3)));

        assertThat(first).isNotEqualTo(second);
        assertThat(first).isNotEqualTo(third);
    }

    @Test
    void firstSubmitCreatesOrderAndMarksIdempotencySucceeded() {
        OrderSubmitDTO dto = submitDTO("宿舍1号楼");
        when(orderIdempotencyMapper.selectByUserIdAndKey(7L, "submit-key")).thenReturn(null);
        when(shoppingCartMapper.selectByUserId(7L)).thenReturn(List.of(cartItem(1L, "10.00", 2)));
        when(dishMapper.selectById(1L)).thenReturn(dish(1L, "测试饭", "10.00"));
        when(orderIdempotencyMapper.insert(any(OrderIdempotency.class))).thenAnswer(invocation -> {
            OrderIdempotency idempotency = invocation.getArgument(0);
            idempotency.setId(55L);
            return 1;
        });
        when(ordersMapper.insert(any(Orders.class))).thenAnswer(invocation -> {
            Orders orders = invocation.getArgument(0);
            orders.setId(101L);
            return 1;
        });
        when(orderDetailMapper.insertBatch(any())).thenReturn(1);
        when(shoppingCartMapper.deleteByUserId(7L)).thenReturn(1);
        when(orderIdempotencyMapper.markSucceeded(eq(55L), eq(101L), eq(OrderIdempotencyStatus.SUCCEEDED.getCode()), any()))
                .thenReturn(1);

        Long orderId = orderService.submit(dto, " submit-key ");

        assertThat(orderId).isEqualTo(101L);
        ArgumentCaptor<Orders> ordersCaptor = ArgumentCaptor.forClass(Orders.class);
        verify(ordersMapper).insert(ordersCaptor.capture());
        assertThat(ordersCaptor.getValue().getUserId()).isEqualTo(7L);
        assertThat(ordersCaptor.getValue().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT.getCode());
        assertThat(ordersCaptor.getValue().getAmount()).isEqualByComparingTo("20.00");
        verify(orderDetailMapper).insertBatch(any());
        verify(shoppingCartMapper).deleteByUserId(7L);
        verify(orderIdempotencyMapper).markSucceeded(eq(55L), eq(101L), eq(OrderIdempotencyStatus.SUCCEEDED.getCode()), any());
        verify(dishStockService).lockStock(101L, Map.of(1L, 2), 7L);
        verify(orderTimeoutOutboxService).createPendingForOrder(101L);
    }

    @Test
    void duplicateSucceededSubmitReturnsOriginalOrderIdWithoutCreatingOrder() {
        String hash = orderService.buildRequestHash(7L, "宿舍1号楼", List.of(cartItem(1L, "10.00", 1)));
        OrderIdempotency existing = existingIdempotency(hash, OrderIdempotencyStatus.SUCCEEDED, 101L);
        when(orderIdempotencyMapper.selectByUserIdAndKey(7L, "submit-key")).thenReturn(existing);
        when(shoppingCartMapper.selectByUserId(7L)).thenReturn(List.of());
        when(orderDetailMapper.selectByOrderId(101L)).thenReturn(List.of(orderDetail(1L, "10.00", 1)));

        Long orderId = orderService.submit(submitDTO("宿舍1号楼"), "submit-key");

        assertThat(orderId).isEqualTo(101L);
        verify(ordersMapper, never()).insert(any());
        verify(orderDetailMapper, never()).insertBatch(any());
        verify(shoppingCartMapper, never()).deleteByUserId(any());
        verify(dishStockService, never()).lockStock(any(), any(), any());
        verify(orderTimeoutOutboxService, never()).createPendingForOrder(any());
    }

    @Test
    void sameKeyDifferentRemarkAfterSucceededThrowsConflictWithoutCreatingOrder() {
        String hash = orderService.buildRequestHash(7L, "第一次备注", List.of(cartItem(1L, "10.00", 1)));
        OrderIdempotency existing = existingIdempotency(hash, OrderIdempotencyStatus.SUCCEEDED, 101L);
        when(orderIdempotencyMapper.selectByUserIdAndKey(7L, "submit-key")).thenReturn(existing);
        when(shoppingCartMapper.selectByUserId(7L)).thenReturn(List.of());
        when(orderDetailMapper.selectByOrderId(101L)).thenReturn(List.of(orderDetail(1L, "10.00", 1)));

        assertThatThrownBy(() -> orderService.submit(submitDTO("第二次备注"), "submit-key"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(409);
        verify(ordersMapper, never()).insert(any());
    }

    @Test
    void sameKeyDifferentContentThrowsConflictWithoutCreatingOrder() {
        String oldHash = orderService.buildRequestHash(7L, "旧备注", List.of(cartItem(1L, "10.00", 1)));
        OrderIdempotency existing = existingIdempotency(oldHash, OrderIdempotencyStatus.SUCCEEDED, 101L);
        when(orderIdempotencyMapper.selectByUserIdAndKey(7L, "submit-key")).thenReturn(existing);
        when(shoppingCartMapper.selectByUserId(7L)).thenReturn(List.of(cartItem(1L, "10.00", 2)));
        when(dishMapper.selectById(1L)).thenReturn(dish(1L, "测试饭", "10.00"));

        assertThatThrownBy(() -> orderService.submit(submitDTO("新备注"), "submit-key"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(409);
        verify(ordersMapper, never()).insert(any());
    }

    @Test
    void processingDuplicateThrowsConflictWithoutCreatingOrder() {
        String hash = orderService.buildRequestHash(7L, "宿舍1号楼", List.of(cartItem(1L, "10.00", 1)));
        OrderIdempotency existing = existingIdempotency(hash, OrderIdempotencyStatus.PROCESSING, null);
        when(orderIdempotencyMapper.selectByUserIdAndKey(7L, "submit-key")).thenReturn(existing);
        when(shoppingCartMapper.selectByUserId(7L)).thenReturn(List.of(cartItem(1L, "10.00", 1)));
        when(dishMapper.selectById(1L)).thenReturn(dish(1L, "测试饭", "10.00"));

        assertThatThrownBy(() -> orderService.submit(submitDTO("宿舍1号楼"), "submit-key"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(409);
        verify(ordersMapper, never()).insert(any());
    }

    @Test
    void firstSubmitAggregatesDuplicateDishItemsBeforeLockingStock() {
        OrderSubmitDTO dto = submitDTO("宿舍1号楼");
        when(orderIdempotencyMapper.selectByUserIdAndKey(7L, "submit-key")).thenReturn(null);
        when(shoppingCartMapper.selectByUserId(7L)).thenReturn(List.of(
                cartItem(1L, "10.00", 1),
                cartItem(1L, "10.00", 2)));
        when(dishMapper.selectById(1L)).thenReturn(dish(1L, "测试饭", "10.00"));
        when(orderIdempotencyMapper.insert(any(OrderIdempotency.class))).thenAnswer(invocation -> {
            OrderIdempotency idempotency = invocation.getArgument(0);
            idempotency.setId(55L);
            return 1;
        });
        when(ordersMapper.insert(any(Orders.class))).thenAnswer(invocation -> {
            Orders orders = invocation.getArgument(0);
            orders.setId(101L);
            return 1;
        });
        when(orderDetailMapper.insertBatch(any())).thenReturn(1);
        when(shoppingCartMapper.deleteByUserId(7L)).thenReturn(1);
        when(orderIdempotencyMapper.markSucceeded(eq(55L), eq(101L), eq(OrderIdempotencyStatus.SUCCEEDED.getCode()), any()))
                .thenReturn(1);

        Long orderId = orderService.submit(dto, "submit-key");

        assertThat(orderId).isEqualTo(101L);
        verify(dishStockService).lockStock(101L, Map.of(1L, 3), 7L);
    }

    @Test
    void stockLockFailureStopsOrderDetailsAndIdempotencySuccess() {
        OrderSubmitDTO dto = submitDTO("宿舍1号楼");
        when(orderIdempotencyMapper.selectByUserIdAndKey(7L, "submit-key")).thenReturn(null);
        when(shoppingCartMapper.selectByUserId(7L)).thenReturn(List.of(cartItem(1L, "10.00", 2)));
        when(dishMapper.selectById(1L)).thenReturn(dish(1L, "测试饭", "10.00"));
        when(orderIdempotencyMapper.insert(any(OrderIdempotency.class))).thenAnswer(invocation -> {
            OrderIdempotency idempotency = invocation.getArgument(0);
            idempotency.setId(55L);
            return 1;
        });
        when(ordersMapper.insert(any(Orders.class))).thenAnswer(invocation -> {
            Orders orders = invocation.getArgument(0);
            orders.setId(101L);
            return 1;
        });
        doThrow(new BusinessException("商品库存不足"))
                .when(dishStockService).lockStock(101L, Map.of(1L, 2), 7L);

        assertThatThrownBy(() -> orderService.submit(dto, "submit-key"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("商品库存不足");
        verify(orderDetailMapper, never()).insertBatch(any());
        verify(shoppingCartMapper, never()).deleteByUserId(any());
        verify(orderIdempotencyMapper, never()).markSucceeded(any(), any(), any(), any());
        verify(orderTimeoutOutboxService, never()).createPendingForOrder(any());
    }

    @Test
    void payCreatesPayingRecordWithoutChangingOrderOrStock() {
        Orders orders = pendingOrder();
        when(redisDistributedLock.tryLock(startsWith("lock:order:status:"), any(), any(Duration.class))).thenReturn(true);
        when(ordersMapper.selectById(101L)).thenReturn(orders);
        when(paymentRecordMapper.selectLatestMockByOrderId(101L, "MOCK")).thenReturn(null);
        when(paymentRecordMapper.insert(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, demo3.demo3_068.entity.PaymentRecord.class).setId(88L);
            return 1;
        });

        OrderPayVO result = orderService.pay(101L);

        assertThat(result.getOrderId()).isEqualTo(101L);
        assertThat(result.getOrderStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT.getCode());
        assertThat(result.getPayStatus()).isEqualTo(PaymentStatus.PAYING.getCode());
        assertThat(result.getTradeNo()).isNotBlank();
        verify(ordersMapper, never()).updateToPaidById(any(), any(), any(), any());
        verify(dishStockService, never()).confirmLockedStock(any(), any(), any());
    }

    @Test
    void payReusesExistingPayingRecord() {
        Orders orders = pendingOrder();
        PaymentRecord existing = paymentRecord("PAY202607090001", PaymentStatus.PAYING);
        when(redisDistributedLock.tryLock(startsWith("lock:order:status:"), any(), any(Duration.class))).thenReturn(true);
        when(ordersMapper.selectById(101L)).thenReturn(orders);
        when(paymentRecordMapper.selectLatestMockByOrderId(101L, "MOCK")).thenReturn(existing);

        OrderPayVO result = orderService.pay(101L);

        assertThat(result.getTradeNo()).isEqualTo("PAY202607090001");
        verify(paymentRecordMapper, never()).insert(any());
        verify(ordersMapper, never()).updateToPaidById(any(), any(), any(), any());
        verify(dishStockService, never()).confirmLockedStock(any(), any(), any());
    }

    @Test
    void payCreatesNewPayingRecordAfterFailedPayment() {
        Orders orders = pendingOrder();
        PaymentRecord failed = paymentRecord("PAY202607090001", PaymentStatus.FAILED);
        when(redisDistributedLock.tryLock(startsWith("lock:order:status:"), any(), any(Duration.class))).thenReturn(true);
        when(ordersMapper.selectById(101L)).thenReturn(orders);
        when(paymentRecordMapper.selectLatestMockByOrderId(101L, "MOCK")).thenReturn(failed);

        OrderPayVO result = orderService.pay(101L);

        assertThat(result.getTradeNo()).isNotEqualTo("PAY202607090001");
        verify(paymentRecordMapper).insert(any(PaymentRecord.class));
        verify(dishStockService, never()).confirmLockedStock(any(), any(), any());
    }

    @Test
    void payLockFailureDoesNotCreatePayingRecord() {
        when(redisDistributedLock.tryLock(startsWith("lock:order:status:"), any(), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> orderService.pay(101L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单处理中，请稍后重试");
        verify(paymentRecordMapper, never()).insert(any());
    }

    @Test
    void cancelReleasesLockedStockAfterStatusUpdateSucceeds() {
        Orders orders = pendingOrder();
        when(redisDistributedLock.tryLock(startsWith("lock:order:status:"), any(), any(Duration.class))).thenReturn(true);
        when(ordersMapper.selectById(101L)).thenReturn(orders);
        when(ordersMapper.updateToCancelledById(eq(101L), any(), eq(OrderStatus.PENDING_PAYMENT.getCode()), eq(OrderStatus.CANCELLED.getCode())))
                .thenReturn(1);
        when(orderDetailMapper.selectByOrderId(101L)).thenReturn(List.of(
                orderDetail(1L, "10.00", 1),
                orderDetail(2L, "12.00", 2)));

        orderService.cancel(101L);

        verify(dishStockService).releaseLockedStock(101L, Map.of(1L, 1, 2L, 2), 7L);
        verify(paymentRecordMapper).closeCurrentPayingMockByOrderId(
                eq(101L), eq("MOCK"), eq(PaymentStatus.PAYING.getCode()), eq(PaymentStatus.CLOSED.getCode()), any());
    }

    @Test
    void duplicateCancelDoesNotReleaseLockedStock() {
        Orders orders = pendingOrder();
        when(redisDistributedLock.tryLock(startsWith("lock:order:status:"), any(), any(Duration.class))).thenReturn(true);
        when(ordersMapper.selectById(101L)).thenReturn(orders);
        when(ordersMapper.updateToCancelledById(eq(101L), any(), eq(OrderStatus.PENDING_PAYMENT.getCode()), eq(OrderStatus.CANCELLED.getCode())))
                .thenReturn(0);

        assertThatThrownBy(() -> orderService.cancel(101L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单状态已变化，请刷新后重试");
        verify(dishStockService, never()).releaseLockedStock(any(), any(), any());
    }

    @Test
    void timeoutCancelPendingOrderReleasesStockWithSystemOperator() {
        Orders orders = pendingOrder();
        when(redisDistributedLock.tryLock(startsWith("lock:order:status:"), any(), any(Duration.class))).thenReturn(true);
        when(ordersMapper.selectById(101L)).thenReturn(orders);
        when(ordersMapper.updateToCancelledById(eq(101L), any(), eq(OrderStatus.PENDING_PAYMENT.getCode()), eq(OrderStatus.CANCELLED.getCode())))
                .thenReturn(1);
        when(userMapper.selectIdByUsername("system_timeout")).thenReturn(999L);
        when(orderDetailMapper.selectByOrderId(101L)).thenReturn(List.of(
                orderDetail(1L, "10.00", 1),
                orderDetail(1L, "10.00", 2)));

        orderService.timeoutCancel(101L, "message-1");

        verify(dishStockService).releaseLockedStock(
                101L,
                Map.of(1L, 3),
                999L,
                "订单超时自动取消释放库存");
        verify(paymentRecordMapper).closeCurrentPayingMockByOrderId(
                eq(101L), eq("MOCK"), eq(PaymentStatus.PAYING.getCode()), eq(PaymentStatus.CLOSED.getCode()), any());
    }

    @Test
    void timeoutCancelNonPendingOrderIsNoOp() {
        Orders orders = pendingOrder();
        orders.setStatus(OrderStatus.PAID.getCode());
        when(redisDistributedLock.tryLock(startsWith("lock:order:status:"), any(), any(Duration.class))).thenReturn(true);
        when(ordersMapper.selectById(101L)).thenReturn(orders);

        orderService.timeoutCancel(101L, "message-1");

        verify(ordersMapper, never()).updateToCancelledById(any(), any(), any(), any());
        verify(dishStockService, never()).releaseLockedStock(any(), any(), any(), any());
    }

    @Test
    void timeoutCancelMissingOrderIsNoOp() {
        when(redisDistributedLock.tryLock(startsWith("lock:order:status:"), any(), any(Duration.class))).thenReturn(true);
        when(ordersMapper.selectById(101L)).thenReturn(null);

        orderService.timeoutCancel(101L, "message-1");

        verify(ordersMapper, never()).updateToCancelledById(any(), any(), any(), any());
        verify(dishStockService, never()).releaseLockedStock(any(), any(), any(), any());
    }

    @Test
    void timeoutCancelDoesNotReleaseStockWhenConditionalUpdateFails() {
        Orders orders = pendingOrder();
        when(redisDistributedLock.tryLock(startsWith("lock:order:status:"), any(), any(Duration.class))).thenReturn(true);
        when(ordersMapper.selectById(101L)).thenReturn(orders);
        when(ordersMapper.updateToCancelledById(eq(101L), any(), eq(OrderStatus.PENDING_PAYMENT.getCode()), eq(OrderStatus.CANCELLED.getCode())))
                .thenReturn(0);

        orderService.timeoutCancel(101L, "message-1");

        verify(dishStockService, never()).releaseLockedStock(any(), any(), any(), any());
    }

    @Test
    void timeoutCancelLockFailureRemainsRetryable() {
        when(redisDistributedLock.tryLock(startsWith("lock:order:status:"), any(), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> orderService.timeoutCancel(101L, "message-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单处理中，请稍后重试");
        verify(ordersMapper, never()).selectById(any());
    }

    private OrderSubmitDTO submitDTO(String remark) {
        OrderSubmitDTO dto = new OrderSubmitDTO();
        dto.setRemark(remark);
        return dto;
    }

    private ShoppingCart cartItem(Long dishId, String dishPrice, int quantity) {
        ShoppingCart cartItem = new ShoppingCart();
        cartItem.setUserId(7L);
        cartItem.setDishId(dishId);
        cartItem.setDishName("测试菜品" + dishId);
        cartItem.setDishPrice(new BigDecimal(dishPrice));
        cartItem.setQuantity(quantity);
        return cartItem;
    }

    private Dish dish(Long id, String name, String price) {
        Dish dish = new Dish();
        dish.setId(id);
        dish.setName(name);
        dish.setPrice(new BigDecimal(price));
        dish.setStatus(1);
        return dish;
    }

    private OrderDetail orderDetail(Long dishId, String dishPrice, int quantity) {
        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setOrderId(101L);
        orderDetail.setDishId(dishId);
        orderDetail.setDishName("测试菜品" + dishId);
        orderDetail.setDishPrice(new BigDecimal(dishPrice));
        orderDetail.setQuantity(quantity);
        orderDetail.setAmount(new BigDecimal(dishPrice).multiply(BigDecimal.valueOf(quantity)));
        return orderDetail;
    }

    private Orders pendingOrder() {
        Orders orders = new Orders();
        orders.setId(101L);
        orders.setNumber("202607080001");
        orders.setUserId(7L);
        orders.setStatus(OrderStatus.PENDING_PAYMENT.getCode());
        orders.setAmount(new BigDecimal("30.00"));
        return orders;
    }

    private PaymentRecord paymentRecord(String tradeNo, PaymentStatus status) {
        PaymentRecord paymentRecord = new PaymentRecord();
        paymentRecord.setId(88L);
        paymentRecord.setOrderId(101L);
        paymentRecord.setOrderNumber("202607080001");
        paymentRecord.setUserId(7L);
        paymentRecord.setAmount(new BigDecimal("30.00"));
        paymentRecord.setPayChannel("MOCK");
        paymentRecord.setTradeNo(tradeNo);
        paymentRecord.setStatus(status.getCode());
        paymentRecord.setRequestTime(java.time.LocalDateTime.now());
        return paymentRecord;
    }

    private OrderIdempotency existingIdempotency(String requestHash,
                                                 OrderIdempotencyStatus status,
                                                 Long orderId) {
        OrderIdempotency orderIdempotency = new OrderIdempotency();
        orderIdempotency.setId(55L);
        orderIdempotency.setUserId(7L);
        orderIdempotency.setIdempotencyKey("submit-key");
        orderIdempotency.setRequestHash(requestHash);
        orderIdempotency.setStatus(status.getCode());
        orderIdempotency.setOrderId(orderId);
        return orderIdempotency;
    }
}
