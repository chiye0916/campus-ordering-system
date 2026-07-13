package demo3.demo3_068.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo3.demo3_068.common.RedisDistributedLock;
import demo3.demo3_068.dto.MockPaymentCallbackDTO;
import demo3.demo3_068.entity.OrderDetail;
import demo3.demo3_068.entity.Orders;
import demo3.demo3_068.entity.PaymentCallbackRecord;
import demo3.demo3_068.entity.PaymentRecord;
import demo3.demo3_068.exception.PaymentCallbackRetryableException;
import demo3.demo3_068.mapper.OrderDetailMapper;
import demo3.demo3_068.mapper.OrdersMapper;
import demo3.demo3_068.mapper.PaymentCallbackRecordMapper;
import demo3.demo3_068.mapper.PaymentRecordMapper;
import demo3.demo3_068.model.MockPayStatus;
import demo3.demo3_068.model.OrderStatus;
import demo3.demo3_068.model.PaymentCallbackProcessStatus;
import demo3.demo3_068.model.PaymentStatus;
import demo3.demo3_068.service.DishStockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentCallbackRecordMapper paymentCallbackRecordMapper;
    @Mock
    private PaymentRecordMapper paymentRecordMapper;
    @Mock
    private OrdersMapper ordersMapper;
    @Mock
    private OrderDetailMapper orderDetailMapper;
    @Mock
    private RedisDistributedLock redisDistributedLock;
    @Mock
    private DishStockService dishStockService;

    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(
                paymentCallbackRecordMapper,
                paymentRecordMapper,
                ordersMapper,
                orderDetailMapper,
                redisDistributedLock,
                dishStockService,
                new ObjectMapper());
        lenient().when(paymentCallbackRecordMapper.finalizeById(any(), any(), any(), any(), any(), any())).thenReturn(1);
    }

    @Test
    void unknownTradeIsRecordedFailedWithoutSideEffects() {
        mockInsertCallbackId();
        when(paymentRecordMapper.selectByTradeNo("PAY001")).thenReturn(null);

        paymentService.handleMockCallback(callback(MockPayStatus.SUCCESS, "30.00"));

        verifyFinalize(PaymentCallbackProcessStatus.FAILED, null, null);
        verify(ordersMapper, never()).updateToPaidById(any(), any(), any(), any());
        verify(dishStockService, never()).confirmLockedStock(any(), any(), any());
    }

    @Test
    void amountMismatchIsRecordedFailedWithoutPaymentSuccess() {
        mockInsertCallbackId();
        when(paymentRecordMapper.selectByTradeNo("PAY001")).thenReturn(paymentRecord(PaymentStatus.PAYING, "31.00"));

        paymentService.handleMockCallback(callback(MockPayStatus.SUCCESS, "30.00"));

        verifyFinalize(PaymentCallbackProcessStatus.FAILED, 88L, 101L);
        verify(paymentRecordMapper, never()).updateStatusToSuccessById(any(), any(), any(), any(), any(), any());
        verify(dishStockService, never()).confirmLockedStock(any(), any(), any());
    }

    @Test
    void failedPaymentCallbackMarksPaymentFailedAndLeavesOrderAndStockUntouched() {
        mockInsertCallbackId();
        when(paymentRecordMapper.selectByTradeNo("PAY001")).thenReturn(paymentRecord(PaymentStatus.PAYING, "30.00"));
        when(paymentRecordMapper.updateStatusToFailedById(
                eq(88L), any(), eq("THIRD001"), any(), eq(PaymentStatus.PAYING.getCode()), eq(PaymentStatus.FAILED.getCode())))
                .thenReturn(1);

        paymentService.handleMockCallback(callback(MockPayStatus.FAILED, "30.00"));

        verifyFinalize(PaymentCallbackProcessStatus.PROCESSED, 88L, 101L);
        verify(ordersMapper, never()).updateToPaidById(any(), any(), any(), any());
        verify(dishStockService, never()).confirmLockedStock(any(), any(), any());
    }

    @Test
    void successfulCallbackUpdatesPaymentOrderAndConfirmsStock() {
        mockInsertCallbackId();
        when(paymentRecordMapper.selectByTradeNo("PAY001")).thenReturn(paymentRecord(PaymentStatus.PAYING, "30.00"));
        when(redisDistributedLock.tryLock(startsWith("lock:order:status:"), any(), any(Duration.class))).thenReturn(true);
        when(ordersMapper.selectById(101L)).thenReturn(order(OrderStatus.PENDING_PAYMENT));
        when(paymentRecordMapper.updateStatusToSuccessById(
                eq(88L), any(), any(), eq("THIRD001"), eq(PaymentStatus.PAYING.getCode()), eq(PaymentStatus.SUCCESS.getCode())))
                .thenReturn(1);
        when(ordersMapper.updateToPaidById(eq(101L), any(), eq(OrderStatus.PENDING_PAYMENT.getCode()), eq(OrderStatus.PAID.getCode())))
                .thenReturn(1);
        when(orderDetailMapper.selectByOrderId(101L)).thenReturn(List.of(
                orderDetail(1L, 1),
                orderDetail(1L, 2)));

        paymentService.handleMockCallback(callback(MockPayStatus.SUCCESS, "30.00"));

        verify(dishStockService).confirmLockedStock(101L, Map.of(1L, 3), 7L);
        verifyFinalize(PaymentCallbackProcessStatus.PROCESSED, 88L, 101L);
    }

    @Test
    void successCallbackLockFailureRemainsRetryable() {
        mockInsertCallbackId();
        when(paymentRecordMapper.selectByTradeNo("PAY001")).thenReturn(paymentRecord(PaymentStatus.PAYING, "30.00"));
        when(redisDistributedLock.tryLock(startsWith("lock:order:status:"), any(), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> paymentService.handleMockCallback(callback(MockPayStatus.SUCCESS, "30.00")))
                .isInstanceOf(PaymentCallbackRetryableException.class);

        verify(paymentCallbackRecordMapper, never()).finalizeById(any(), any(), any(), any(), any(), any());
        verify(paymentRecordMapper, never()).updateStatusToSuccessById(any(), any(), any(), any(), any(), any());
    }

    @Test
    void terminalDuplicateCallbackNumberReturnsWithoutSideEffects() {
        when(paymentCallbackRecordMapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));
        when(paymentCallbackRecordMapper.selectByCallbackNo("CB001")).thenReturn(existingCallback(PaymentCallbackProcessStatus.PROCESSED, LocalDateTime.now()));

        paymentService.handleMockCallback(callback(MockPayStatus.SUCCESS, "30.00"));

        verify(paymentRecordMapper, never()).selectByTradeNo(any());
        verify(dishStockService, never()).confirmLockedStock(any(), any(), any());
    }

    @Test
    void recentProcessingCallbackNumberIsRetryableNotTerminalSuccess() {
        when(paymentCallbackRecordMapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));
        when(paymentCallbackRecordMapper.selectByCallbackNo("CB001")).thenReturn(existingCallback(PaymentCallbackProcessStatus.PROCESSING, LocalDateTime.now()));

        assertThatThrownBy(() -> paymentService.handleMockCallback(callback(MockPayStatus.SUCCESS, "30.00")))
                .isInstanceOf(PaymentCallbackRetryableException.class);

        verify(paymentRecordMapper, never()).selectByTradeNo(any());
    }

    @Test
    void staleProcessingCallbackCanBeReclaimedAndRevalidated() {
        LocalDateTime staleTime = LocalDateTime.now().minusMinutes(10);
        when(paymentCallbackRecordMapper.insert(any())).thenThrow(new DuplicateKeyException("duplicate"));
        when(paymentCallbackRecordMapper.selectByCallbackNo("CB001")).thenReturn(existingCallback(PaymentCallbackProcessStatus.PROCESSING, staleTime));
        when(paymentCallbackRecordMapper.updateProcessingForRetry(eq(99L), any(), any())).thenReturn(1);
        when(paymentRecordMapper.selectByTradeNo("PAY001")).thenReturn(null);

        paymentService.handleMockCallback(callback(MockPayStatus.SUCCESS, "30.00"));

        verify(paymentCallbackRecordMapper).updateProcessingForRetry(eq(99L), any(), any());
        verifyFinalize(PaymentCallbackProcessStatus.FAILED, null, null);
    }

    @Test
    void newCallbackNumberForSuccessfulTradeIsRecordedDuplicateWithoutStock() {
        mockInsertCallbackId();
        when(paymentRecordMapper.selectByTradeNo("PAY001")).thenReturn(paymentRecord(PaymentStatus.SUCCESS, "30.00"));

        paymentService.handleMockCallback(callback(MockPayStatus.SUCCESS, "30.00"));

        verifyFinalize(PaymentCallbackProcessStatus.DUPLICATE, 88L, 101L);
        verify(dishStockService, never()).confirmLockedStock(any(), any(), any());
    }

    @Test
    void successCallbackForClosedPaymentIsIgnoredWithoutReopeningOrder() {
        mockInsertCallbackId();
        when(paymentRecordMapper.selectByTradeNo("PAY001")).thenReturn(paymentRecord(PaymentStatus.CLOSED, "30.00"));

        paymentService.handleMockCallback(callback(MockPayStatus.SUCCESS, "30.00"));

        verifyFinalize(PaymentCallbackProcessStatus.IGNORED, 88L, 101L);
        verify(paymentRecordMapper, never()).updateStatusToSuccessById(any(), any(), any(), any(), any(), any());
        verify(ordersMapper, never()).updateToPaidById(any(), any(), any(), any());
    }

    @Test
    void successCallbackStopsBeforeStockWhenPaymentConditionalUpdateAffectsZeroRows() {
        mockInsertCallbackId();
        PaymentRecord paying = paymentRecord(PaymentStatus.PAYING, "30.00");
        PaymentRecord currentSuccess = paymentRecord(PaymentStatus.SUCCESS, "30.00");
        when(paymentRecordMapper.selectByTradeNo("PAY001")).thenReturn(paying, currentSuccess);
        when(redisDistributedLock.tryLock(startsWith("lock:order:status:"), any(), any(Duration.class))).thenReturn(true);
        when(ordersMapper.selectById(101L)).thenReturn(order(OrderStatus.PENDING_PAYMENT));
        when(paymentRecordMapper.updateStatusToSuccessById(any(), any(), any(), any(), any(), any())).thenReturn(0);

        paymentService.handleMockCallback(callback(MockPayStatus.SUCCESS, "30.00"));

        verifyFinalize(PaymentCallbackProcessStatus.DUPLICATE, 88L, 101L);
        verify(ordersMapper, never()).updateToPaidById(any(), any(), any(), any());
        verify(dishStockService, never()).confirmLockedStock(any(), any(), any());
    }

    private void mockInsertCallbackId() {
        when(paymentCallbackRecordMapper.insert(any())).thenAnswer(invocation -> {
            PaymentCallbackRecord record = invocation.getArgument(0);
            record.setId(99L);
            return 1;
        });
    }

    private void verifyFinalize(PaymentCallbackProcessStatus status, Long paymentRecordId, Long orderId) {
        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(paymentCallbackRecordMapper).finalizeById(
                eq(99L),
                eq(paymentRecordId),
                eq(orderId),
                statusCaptor.capture(),
                any(),
                any());
        org.assertj.core.api.Assertions.assertThat(statusCaptor.getValue()).isEqualTo(status.getCode());
    }

    private MockPaymentCallbackDTO callback(String status, String amount) {
        MockPaymentCallbackDTO dto = new MockPaymentCallbackDTO();
        dto.setTradeNo("PAY001");
        dto.setCallbackNo("CB001");
        dto.setThirdTradeNo("THIRD001");
        dto.setPayStatus(status);
        dto.setAmount(new BigDecimal(amount));
        dto.setCallbackTime(LocalDateTime.of(2026, 7, 9, 10, 0));
        return dto;
    }

    private PaymentRecord paymentRecord(PaymentStatus status, String amount) {
        PaymentRecord paymentRecord = new PaymentRecord();
        paymentRecord.setId(88L);
        paymentRecord.setOrderId(101L);
        paymentRecord.setOrderNumber("202607090001");
        paymentRecord.setUserId(7L);
        paymentRecord.setAmount(new BigDecimal(amount));
        paymentRecord.setPayChannel("MOCK");
        paymentRecord.setTradeNo("PAY001");
        paymentRecord.setStatus(status.getCode());
        paymentRecord.setRequestTime(LocalDateTime.now());
        return paymentRecord;
    }

    private PaymentCallbackRecord existingCallback(PaymentCallbackProcessStatus status, LocalDateTime updateTime) {
        PaymentCallbackRecord record = new PaymentCallbackRecord();
        record.setId(99L);
        record.setCallbackNo("CB001");
        record.setTradeNo("PAY001");
        record.setPayStatus(MockPayStatus.SUCCESS);
        record.setAmount(new BigDecimal("30.00"));
        record.setProcessStatus(status.getCode());
        record.setCreateTime(updateTime);
        record.setUpdateTime(updateTime);
        return record;
    }

    private Orders order(OrderStatus status) {
        Orders orders = new Orders();
        orders.setId(101L);
        orders.setUserId(7L);
        orders.setStatus(status.getCode());
        return orders;
    }

    private OrderDetail orderDetail(Long dishId, int quantity) {
        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setOrderId(101L);
        orderDetail.setDishId(dishId);
        orderDetail.setQuantity(quantity);
        return orderDetail;
    }
}
