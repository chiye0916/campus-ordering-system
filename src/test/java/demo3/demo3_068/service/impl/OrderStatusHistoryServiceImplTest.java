package demo3.demo3_068.service.impl;

import demo3.demo3_068.entity.OrderStatusHistory;
import demo3.demo3_068.entity.Orders;
import demo3.demo3_068.mapper.OrderStatusHistoryMapper;
import demo3.demo3_068.mapper.UserMapper;
import demo3.demo3_068.model.OrderStatus;
import demo3.demo3_068.model.OrderStatusChangeOperation;
import demo3.demo3_068.model.Role;
import demo3.demo3_068.observability.TraceContext;
import demo3.demo3_068.vo.OrderStatusHistoryVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatusHistoryServiceImplTest {

    @Mock
    private OrderStatusHistoryMapper orderStatusHistoryMapper;
    @Mock
    private UserMapper userMapper;

    @AfterEach
    void tearDown() {
        org.slf4j.MDC.clear();
    }

    @Test
    void recordChangeMapsSnapshotTraceAndDefaultReason() {
        org.slf4j.MDC.put(TraceContext.TRACE_ID, "trace-12345678");
        OrderStatusHistoryServiceImpl service = new OrderStatusHistoryServiceImpl(orderStatusHistoryMapper, userMapper);
        Orders orders = order();

        service.recordChange(
                orders,
                OrderStatus.PENDING_PAYMENT.getCode(),
                OrderStatus.PAID,
                OrderStatusChangeOperation.PAYMENT_SUCCESS,
                88L,
                Role.SYSTEM,
                " ");

        ArgumentCaptor<OrderStatusHistory> captor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryMapper).insert(captor.capture());
        OrderStatusHistory saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(101L);
        assertThat(saved.getOrderNumber()).isEqualTo("202607160001");
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getOldStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT.getCode());
        assertThat(saved.getNewStatus()).isEqualTo(OrderStatus.PAID.getCode());
        assertThat(saved.getOperation()).isEqualTo("PAYMENT_SUCCESS");
        assertThat(saved.getOperatorId()).isEqualTo(88L);
        assertThat(saved.getOperatorRole()).isEqualTo("SYSTEM");
        assertThat(saved.getReason()).isEqualTo("支付成功确认订单状态");
        assertThat(saved.getTraceId()).isEqualTo("trace-12345678");
        assertThat(saved.getCreateTime()).isNotNull();
    }

    @Test
    void recordSystemChangeAllowsMissingSystemOperatorId() {
        OrderStatusHistoryServiceImpl service = new OrderStatusHistoryServiceImpl(orderStatusHistoryMapper, userMapper);
        when(userMapper.selectIdByUsername("system_timeout")).thenReturn(null);

        service.recordSystemChange(order(), OrderStatus.PENDING_PAYMENT.getCode(), OrderStatus.CANCELLED,
                OrderStatusChangeOperation.TIMEOUT_CANCEL, null);

        ArgumentCaptor<OrderStatusHistory> captor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(orderStatusHistoryMapper).insert(captor.capture());
        assertThat(captor.getValue().getOperatorId()).isNull();
        assertThat(captor.getValue().getOperatorRole()).isEqualTo("SYSTEM");
    }

    @Test
    void listByOrderIdConvertsStatusAndOperationLabels() {
        OrderStatusHistoryServiceImpl service = new OrderStatusHistoryServiceImpl(orderStatusHistoryMapper, userMapper);
        OrderStatusHistory history = new OrderStatusHistory();
        history.setId(1L);
        history.setOrderId(101L);
        history.setOrderNumber("202607160001");
        history.setUserId(7L);
        history.setOldStatus(OrderStatus.PAID.getCode());
        history.setNewStatus(OrderStatus.ACCEPTED.getCode());
        history.setOperation(OrderStatusChangeOperation.MERCHANT_ACCEPT.name());
        history.setOperatorId(9L);
        history.setOperatorRole(Role.MERCHANT.name());
        history.setReason("商家接单");
        history.setTraceId("trace-12345678");
        history.setCreateTime(LocalDateTime.now());
        when(orderStatusHistoryMapper.selectByOrderId(101L)).thenReturn(List.of(history));

        List<OrderStatusHistoryVO> records = service.listByOrderId(101L);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).getOldStatusLabel()).isEqualTo("已支付");
        assertThat(records.get(0).getNewStatusLabel()).isEqualTo("已接单");
        assertThat(records.get(0).getOperationText()).isEqualTo("商家接单");
    }

    @Test
    void recordChangeRequiresCallerTransactionByAnnotation() throws Exception {
        Method method = OrderStatusHistoryServiceImpl.class.getMethod("recordChange",
                Orders.class, Integer.class, OrderStatus.class, OrderStatusChangeOperation.class,
                Long.class, Role.class, String.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.MANDATORY);
    }

    private Orders order() {
        Orders orders = new Orders();
        orders.setId(101L);
        orders.setNumber("202607160001");
        orders.setUserId(7L);
        orders.setStatus(OrderStatus.PENDING_PAYMENT.getCode());
        orders.setAmount(new BigDecimal("30.00"));
        orders.setOrderTime(LocalDateTime.now());
        return orders;
    }
}
