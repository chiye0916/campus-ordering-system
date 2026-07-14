package demo3.demo3_068.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import demo3.demo3_068.common.Constants;
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
import demo3.demo3_068.observability.BusinessMetrics;
import demo3.demo3_068.observability.TraceContext;
import demo3.demo3_068.service.DishStockService;
import demo3.demo3_068.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Duration ORDER_STATUS_LOCK_TTL = Duration.ofSeconds(30);
    private static final Duration PROCESSING_STALE_AFTER = Duration.ofMinutes(5);
    private static final String PAYMENT_CHANNEL_MOCK = "MOCK";

    private final PaymentCallbackRecordMapper paymentCallbackRecordMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final OrdersMapper ordersMapper;
    private final OrderDetailMapper orderDetailMapper;
    private final RedisDistributedLock redisDistributedLock;
    private final DishStockService dishStockService;
    private final ObjectMapper objectMapper;
    private final BusinessMetrics businessMetrics;

    public PaymentServiceImpl(PaymentCallbackRecordMapper paymentCallbackRecordMapper,
                              PaymentRecordMapper paymentRecordMapper,
                              OrdersMapper ordersMapper,
                              OrderDetailMapper orderDetailMapper,
                              RedisDistributedLock redisDistributedLock,
                              DishStockService dishStockService,
                              ObjectMapper objectMapper,
                              BusinessMetrics businessMetrics) {
        this.paymentCallbackRecordMapper = paymentCallbackRecordMapper;
        this.paymentRecordMapper = paymentRecordMapper;
        this.ordersMapper = ordersMapper;
        this.orderDetailMapper = orderDetailMapper;
        this.redisDistributedLock = redisDistributedLock;
        this.dishStockService = dishStockService;
        this.objectMapper = objectMapper;
        this.businessMetrics = businessMetrics;
    }

    @Override
    @Transactional
    public void handleMockCallback(MockPaymentCallbackDTO callbackDTO) {
        LocalDateTime now = LocalDateTime.now();
        String rawPayload = serializePayload(callbackDTO);
        PaymentCallbackRecord callbackRecord = null;
        PaymentRecord paymentRecord = null;
        try {
            callbackRecord = createOrLoadProcessingRecord(callbackDTO, rawPayload, now);
            if (callbackRecord == null) {
                businessMetrics.recordPaymentCallback("duplicate");
                log.info("Payment callback outcome traceId={} result=duplicate tradeNo={} callbackNo={} orderId={} paymentRecordId={}",
                        traceId(), callbackDTO.getTradeNo(), callbackDTO.getCallbackNo(), null, null);
                return;
            }

            paymentRecord = paymentRecordMapper.selectByTradeNo(callbackDTO.getTradeNo());
            if (paymentRecord == null) {
                finalizeCallback(callbackRecord, null, null, PaymentCallbackProcessStatus.FAILED, "未知支付流水", now);
                businessMetrics.recordPaymentCallback("ignored");
                log.info("Payment callback outcome traceId={} result=ignored reason=unknown_trade tradeNo={} callbackNo={} orderId={} paymentRecordId={}",
                        traceId(), callbackDTO.getTradeNo(), callbackDTO.getCallbackNo(), null, null);
                return;
            }
            if (callbackDTO.getAmount().compareTo(paymentRecord.getAmount()) != 0) {
                finalizeCallback(callbackRecord, paymentRecord, PaymentCallbackProcessStatus.FAILED, "回调金额不匹配", now);
                businessMetrics.recordPaymentCallback("amount_mismatch");
                log.warn("Payment callback outcome traceId={} result=amount_mismatch tradeNo={} callbackNo={} orderId={} paymentRecordId={}",
                        traceId(), callbackDTO.getTradeNo(), callbackDTO.getCallbackNo(), paymentRecord.getOrderId(), paymentRecord.getId());
                return;
            }

            if (MockPayStatus.FAILED.equals(callbackDTO.getPayStatus())) {
                handleFailedPaymentCallback(callbackDTO, callbackRecord, paymentRecord, now);
                return;
            }

            handleSuccessfulPaymentCallback(callbackDTO, callbackRecord, paymentRecord, now);
        } catch (PaymentCallbackRetryableException e) {
            businessMetrics.recordPaymentCallback("retryable_conflict");
            log.warn("Payment callback outcome traceId={} result=retryable_conflict tradeNo={} callbackNo={} orderId={} paymentRecordId={} message={}",
                    traceId(), callbackDTO.getTradeNo(), callbackDTO.getCallbackNo(),
                    paymentRecord == null ? null : paymentRecord.getOrderId(),
                    paymentRecord == null ? null : paymentRecord.getId(), e.getMessage());
            throw e;
        }
    }

    private PaymentCallbackRecord createOrLoadProcessingRecord(MockPaymentCallbackDTO callbackDTO,
                                                               String rawPayload,
                                                               LocalDateTime now) {
        PaymentCallbackRecord callbackRecord = buildProcessingCallbackRecord(callbackDTO, rawPayload, now);
        try {
            paymentCallbackRecordMapper.insert(callbackRecord);
            return callbackRecord;
        } catch (DuplicateKeyException e) {
            PaymentCallbackRecord existing = paymentCallbackRecordMapper.selectByCallbackNo(callbackDTO.getCallbackNo());
            if (existing == null) {
                throw e;
            }
            warnIfInconsistent(callbackDTO, existing);
            if (PaymentCallbackProcessStatus.isTerminal(existing.getProcessStatus())) {
                return null;
            }
            LocalDateTime updateTime = existing.getUpdateTime() == null ? existing.getCreateTime() : existing.getUpdateTime();
            if (updateTime != null && updateTime.isAfter(now.minus(PROCESSING_STALE_AFTER))) {
                throw new PaymentCallbackRetryableException("支付回调正在处理中，请稍后重试");
            }
            int rows = paymentCallbackRecordMapper.updateProcessingForRetry(existing.getId(), rawPayload, now);
            if (rows == 0) {
                throw new PaymentCallbackRetryableException("支付回调正在处理中，请稍后重试");
            }
            existing.setRawPayload(rawPayload);
            existing.setUpdateTime(now);
            return existing;
        }
    }

    private void handleFailedPaymentCallback(MockPaymentCallbackDTO callbackDTO,
                                             PaymentCallbackRecord callbackRecord,
                                             PaymentRecord paymentRecord,
                                             LocalDateTime now) {
        if (PaymentStatus.PAYING.getCode() == paymentRecord.getStatus()) {
            int rows = paymentRecordMapper.updateStatusToFailedById(
                    paymentRecord.getId(),
                    callbackDTO.getCallbackTime(),
                    callbackDTO.getThirdTradeNo(),
                    "模拟支付失败回调",
                    PaymentStatus.PAYING.getCode(),
                    PaymentStatus.FAILED.getCode());
            if (rows == 1) {
                finalizeCallback(callbackRecord, paymentRecord, PaymentCallbackProcessStatus.PROCESSED, null, now);
                businessMetrics.recordPaymentCallback("failed");
                log.info("Payment callback outcome traceId={} result=failed tradeNo={} callbackNo={} orderId={} paymentRecordId={}",
                        traceId(), callbackDTO.getTradeNo(), callbackDTO.getCallbackNo(), paymentRecord.getOrderId(), paymentRecord.getId());
                return;
            }
            paymentRecord = paymentRecordMapper.selectByTradeNo(callbackDTO.getTradeNo());
        }
        finalizeCallback(callbackRecord, paymentRecord, terminalNoopStatus(paymentRecord), "支付流水已是终态，失败回调不再变更业务状态", now);
        businessMetrics.recordPaymentCallback("ignored");
        log.info("Payment callback outcome traceId={} result=ignored reason=terminal tradeNo={} callbackNo={} orderId={} paymentRecordId={}",
                traceId(), callbackDTO.getTradeNo(), callbackDTO.getCallbackNo(), paymentRecord.getOrderId(), paymentRecord.getId());
    }

    private void handleSuccessfulPaymentCallback(MockPaymentCallbackDTO callbackDTO,
                                                 PaymentCallbackRecord callbackRecord,
                                                 PaymentRecord paymentRecord,
                                                 LocalDateTime now) {
        if (PaymentStatus.SUCCESS.getCode() == paymentRecord.getStatus()) {
            finalizeCallback(callbackRecord, paymentRecord, PaymentCallbackProcessStatus.DUPLICATE, "支付流水已成功", now);
            businessMetrics.recordPaymentCallback("duplicate");
            log.info("Payment callback outcome traceId={} result=duplicate tradeNo={} callbackNo={} orderId={} paymentRecordId={}",
                    traceId(), callbackDTO.getTradeNo(), callbackDTO.getCallbackNo(), paymentRecord.getOrderId(), paymentRecord.getId());
            return;
        }
        if (PaymentStatus.PAYING.getCode() != paymentRecord.getStatus()) {
            finalizeCallback(callbackRecord, paymentRecord, PaymentCallbackProcessStatus.IGNORED, "支付流水状态不允许成功回调", now);
            businessMetrics.recordPaymentCallback("ignored");
            log.info("Payment callback outcome traceId={} result=ignored reason=payment_status tradeNo={} callbackNo={} orderId={} paymentRecordId={}",
                    traceId(), callbackDTO.getTradeNo(), callbackDTO.getCallbackNo(), paymentRecord.getOrderId(), paymentRecord.getId());
            return;
        }

        String lockKey = Constants.ORDER_STATUS_LOCK_KEY_PREFIX + paymentRecord.getOrderId();
        String lockValue = UUID.randomUUID().toString();
        if (!redisDistributedLock.tryLock(lockKey, lockValue, ORDER_STATUS_LOCK_TTL)) {
            throw new PaymentCallbackRetryableException(Constants.ORDER_STATUS_LOCK_FAILED_MESSAGE);
        }
        try {
            processSuccessfulCallbackWithLock(callbackDTO, callbackRecord, paymentRecord, now);
        } finally {
            releaseOrderStatusLock(lockKey, lockValue);
        }
    }

    private void processSuccessfulCallbackWithLock(MockPaymentCallbackDTO callbackDTO,
                                                   PaymentCallbackRecord callbackRecord,
                                                   PaymentRecord paymentRecord,
                                                   LocalDateTime now) {
        Orders orders = ordersMapper.selectById(paymentRecord.getOrderId());
        if (orders == null || !Integer.valueOf(OrderStatus.PENDING_PAYMENT.getCode()).equals(orders.getStatus())) {
            finalizeCallback(callbackRecord, paymentRecord, PaymentCallbackProcessStatus.IGNORED, "订单状态不允许支付成功", now);
            businessMetrics.recordPaymentCallback("ignored");
            log.info("Payment callback outcome traceId={} result=ignored reason=order_status tradeNo={} callbackNo={} orderId={} paymentRecordId={}",
                    traceId(), callbackDTO.getTradeNo(), callbackDTO.getCallbackNo(), paymentRecord.getOrderId(), paymentRecord.getId());
            return;
        }

        int paymentRows = paymentRecordMapper.updateStatusToSuccessById(
                paymentRecord.getId(),
                callbackDTO.getCallbackTime(),
                callbackDTO.getCallbackTime(),
                callbackDTO.getThirdTradeNo(),
                PaymentStatus.PAYING.getCode(),
                PaymentStatus.SUCCESS.getCode());
        if (paymentRows == 0) {
            PaymentRecord current = paymentRecordMapper.selectByTradeNo(callbackDTO.getTradeNo());
            finalizeCallback(callbackRecord, current, terminalNoopStatus(current), "支付流水状态已变化", now);
            businessMetrics.recordPaymentCallback(callbackResultFor(current));
            log.info("Payment callback outcome traceId={} result={} reason=payment_state_changed tradeNo={} callbackNo={} orderId={} paymentRecordId={}",
                    traceId(), callbackResultFor(current), callbackDTO.getTradeNo(), callbackDTO.getCallbackNo(),
                    current == null ? null : current.getOrderId(), current == null ? null : current.getId());
            return;
        }

        int orderRows = ordersMapper.updateToPaidById(
                paymentRecord.getOrderId(),
                callbackDTO.getCallbackTime(),
                OrderStatus.PENDING_PAYMENT.getCode(),
                OrderStatus.PAID.getCode());
        if (orderRows == 0) {
            throw new PaymentCallbackRetryableException("订单状态已变化，请稍后重试");
        }

        dishStockService.confirmLockedStock(
                paymentRecord.getOrderId(),
                aggregateOrderDetailQuantities(orderDetailMapper.selectByOrderId(paymentRecord.getOrderId())),
                paymentRecord.getUserId());
        finalizeCallback(callbackRecord, paymentRecord, PaymentCallbackProcessStatus.PROCESSED, null, now);
        businessMetrics.recordPaymentCallback("success");
        log.info("Payment callback outcome traceId={} result=success tradeNo={} callbackNo={} orderId={} paymentRecordId={}",
                traceId(), callbackDTO.getTradeNo(), callbackDTO.getCallbackNo(), paymentRecord.getOrderId(), paymentRecord.getId());
    }

    private PaymentCallbackProcessStatus terminalNoopStatus(PaymentRecord paymentRecord) {
        if (paymentRecord != null && PaymentStatus.SUCCESS.getCode() == paymentRecord.getStatus()) {
            return PaymentCallbackProcessStatus.DUPLICATE;
        }
        return PaymentCallbackProcessStatus.IGNORED;
    }

    private void finalizeCallback(PaymentCallbackRecord callbackRecord,
                                  PaymentRecord paymentRecord,
                                  PaymentCallbackProcessStatus processStatus,
                                  String failureReason,
                                  LocalDateTime now) {
        finalizeCallback(
                callbackRecord,
                paymentRecord == null ? null : paymentRecord.getId(),
                paymentRecord == null ? null : paymentRecord.getOrderId(),
                processStatus,
                failureReason,
                now);
    }

    private void finalizeCallback(PaymentCallbackRecord callbackRecord,
                                  Long paymentRecordId,
                                  Long orderId,
                                  PaymentCallbackProcessStatus processStatus,
                                  String failureReason,
                                  LocalDateTime now) {
        int rows = paymentCallbackRecordMapper.finalizeById(
                callbackRecord.getId(),
                paymentRecordId,
                orderId,
                processStatus.getCode(),
                failureReason,
                now);
        if (rows == 0) {
            throw new PaymentCallbackRetryableException("支付回调状态已变化，请稍后重试");
        }
    }

    private PaymentCallbackRecord buildProcessingCallbackRecord(MockPaymentCallbackDTO callbackDTO,
                                                                String rawPayload,
                                                                LocalDateTime now) {
        PaymentCallbackRecord callbackRecord = new PaymentCallbackRecord();
        callbackRecord.setTradeNo(callbackDTO.getTradeNo());
        callbackRecord.setCallbackNo(callbackDTO.getCallbackNo());
        callbackRecord.setThirdTradeNo(callbackDTO.getThirdTradeNo());
        callbackRecord.setPayStatus(callbackDTO.getPayStatus());
        callbackRecord.setAmount(callbackDTO.getAmount());
        callbackRecord.setCallbackTime(callbackDTO.getCallbackTime());
        callbackRecord.setProcessStatus(PaymentCallbackProcessStatus.PROCESSING.getCode());
        callbackRecord.setRawPayload(rawPayload);
        callbackRecord.setCreateTime(now);
        callbackRecord.setUpdateTime(now);
        return callbackRecord;
    }

    private void warnIfInconsistent(MockPaymentCallbackDTO callbackDTO, PaymentCallbackRecord existing) {
        boolean inconsistent = !callbackDTO.getTradeNo().equals(existing.getTradeNo())
                || callbackDTO.getAmount().compareTo(existing.getAmount()) != 0
                || !callbackDTO.getPayStatus().equals(existing.getPayStatus());
        if (inconsistent) {
            log.warn("重复支付回调号 payload 不一致，callbackNo={}, oldTradeNo={}, newTradeNo={}",
                    existing.getCallbackNo(), existing.getTradeNo(), callbackDTO.getTradeNo());
        }
    }

    private String serializePayload(MockPaymentCallbackDTO callbackDTO) {
        try {
            return objectMapper.writeValueAsString(callbackDTO);
        } catch (JsonProcessingException e) {
            return callbackDTO.toString();
        }
    }

    private Map<Long, Integer> aggregateOrderDetailQuantities(java.util.List<OrderDetail> details) {
        return details.stream()
                .collect(Collectors.groupingBy(
                        OrderDetail::getDishId,
                        Collectors.summingInt(OrderDetail::getQuantity)));
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

    private String callbackResultFor(PaymentRecord paymentRecord) {
        if (paymentRecord != null && PaymentStatus.SUCCESS.getCode() == paymentRecord.getStatus()) {
            return "duplicate";
        }
        return "ignored";
    }

    private String traceId() {
        return org.slf4j.MDC.get(TraceContext.TRACE_ID);
    }
}
