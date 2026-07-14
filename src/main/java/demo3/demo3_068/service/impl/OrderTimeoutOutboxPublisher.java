package demo3.demo3_068.service.impl;

import demo3.demo3_068.common.OrderTimeoutRabbitConstants;
import demo3.demo3_068.config.OrderTimeoutProperties;
import demo3.demo3_068.entity.OrderTimeoutOutbox;
import demo3.demo3_068.mapper.OrderTimeoutOutboxMapper;
import demo3.demo3_068.model.OrderTimeoutOutboxStatus;
import demo3.demo3_068.observability.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OrderTimeoutOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutOutboxPublisher.class);
    private static final int LAST_ERROR_MAX_LENGTH = 512;

    private final OrderTimeoutOutboxMapper orderTimeoutOutboxMapper;
    private final RabbitTemplate rabbitTemplate;
    private final OrderTimeoutProperties orderTimeoutProperties;
    private final TraceContext traceContext;

    public OrderTimeoutOutboxPublisher(OrderTimeoutOutboxMapper orderTimeoutOutboxMapper,
                                       RabbitTemplate rabbitTemplate,
                                       OrderTimeoutProperties orderTimeoutProperties,
                                       TraceContext traceContext) {
        this.orderTimeoutOutboxMapper = orderTimeoutOutboxMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.orderTimeoutProperties = orderTimeoutProperties;
        this.traceContext = traceContext;
    }

    @Scheduled(fixedDelayString = "${order.timeout.outbox.publish-fixed-delay:5s}")
    public void publishDueMessages() {
        try (TraceContext.Scope ignored = traceContext.open(traceContext.currentTraceId())) {
            recoverStalePublishingRows();
            LocalDateTime now = LocalDateTime.now();
            List<OrderTimeoutOutbox> dueRows = orderTimeoutOutboxMapper.selectDueRows(
                    now,
                    OrderTimeoutOutboxStatus.PENDING.getCode(),
                    OrderTimeoutOutboxStatus.FAILED.getCode(),
                    orderTimeoutProperties.getOutbox().getMaxRetryCount(),
                    orderTimeoutProperties.getOutbox().getPublishBatchSize());
            for (OrderTimeoutOutbox row : dueRows) {
                publishIfClaimed(row);
            }
        }
    }

    void recoverStalePublishingRows() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime claimDeadline = now.minus(orderTimeoutProperties.getOutbox().getPublisherClaimTimeout());
        int rows = orderTimeoutOutboxMapper.recoverStalePublishing(
                now,
                claimDeadline,
                now.plus(orderTimeoutProperties.getOutbox().getRetryDelay()),
                OrderTimeoutOutboxStatus.PUBLISHING.getCode(),
                OrderTimeoutOutboxStatus.FAILED.getCode(),
                "Publishing claim timed out",
                orderTimeoutProperties.getOutbox().getMaxRetryCount());
        if (rows > 0) {
            log.warn("Outbox publisher recovery traceId={} result=stale_recovered recoveredCount={}",
                    traceContext.currentTraceId(), rows);
        }
    }

    private void publishIfClaimed(OrderTimeoutOutbox row) {
        try (TraceContext.Scope ignored = traceContext.open(row.getTraceId())) {
            LocalDateTime now = LocalDateTime.now();
            int claimed = orderTimeoutOutboxMapper.claimForPublishing(
                    row.getId(),
                    now,
                    OrderTimeoutOutboxStatus.PENDING.getCode(),
                    OrderTimeoutOutboxStatus.FAILED.getCode(),
                    OrderTimeoutOutboxStatus.PUBLISHING.getCode(),
                    orderTimeoutProperties.getOutbox().getMaxRetryCount());
            if (claimed == 0) {
                return;
            }
            log.info("Outbox publisher claim traceId={} result=claimed messageId={} orderId={}",
                    traceContext.currentTraceId(), row.getMessageId(), row.getOrderId());

            try {
                log.info("Outbox publisher publish attempt traceId={} messageId={} orderId={}",
                        traceContext.currentTraceId(), row.getMessageId(), row.getOrderId());
                publishAndWaitForConfirm(row);
                orderTimeoutOutboxMapper.markSent(
                        row.getId(),
                        LocalDateTime.now(),
                        OrderTimeoutOutboxStatus.PUBLISHING.getCode(),
                        OrderTimeoutOutboxStatus.SENT.getCode());
                log.info("Outbox publisher confirm success traceId={} result=sent messageId={} orderId={}",
                        traceContext.currentTraceId(), row.getMessageId(), row.getOrderId());
            } catch (Exception e) {
                markPublishFailed(row, e);
                log.warn("Outbox publisher confirm failure traceId={} result=retry messageId={} orderId={} retryCount={} message={}",
                        traceContext.currentTraceId(), row.getMessageId(), row.getOrderId(),
                        row.getRetryCount() == null ? null : row.getRetryCount() + 1, e.getMessage(), e);
            }
        }
    }

    private void publishAndWaitForConfirm(OrderTimeoutOutbox row) throws Exception {
        CorrelationData correlationData = new CorrelationData(row.getMessageId());
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setMessageId(row.getMessageId());
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        if (traceContext.currentTraceId() != null) {
            messageProperties.setHeader(TraceContext.TRACE_HEADER, traceContext.currentTraceId());
        }
        Message message = new Message(row.getPayload().getBytes(StandardCharsets.UTF_8), messageProperties);
        rabbitTemplate.send(
                OrderTimeoutRabbitConstants.DELAY_EXCHANGE,
                OrderTimeoutRabbitConstants.DELAY_ROUTING_KEY,
                message,
                correlationData);
        CorrelationData.Confirm confirm = correlationData.getFuture().get(
                orderTimeoutProperties.getOutbox().getPublisherConfirmTimeout().toMillis(),
                TimeUnit.MILLISECONDS);
        if (!confirm.isAck()) {
            throw new IllegalStateException("RabbitMQ publisher confirm nack: " + confirm.getReason());
        }
    }

    private void markPublishFailed(OrderTimeoutOutbox row, Exception e) {
        LocalDateTime now = LocalDateTime.now();
        orderTimeoutOutboxMapper.markPublishFailed(
                row.getId(),
                truncate(e.getMessage()),
                now,
                now.plus(orderTimeoutProperties.getOutbox().getRetryDelay()),
                OrderTimeoutOutboxStatus.PUBLISHING.getCode(),
                OrderTimeoutOutboxStatus.FAILED.getCode());
        int nextRetryCount = row.getRetryCount() == null ? 1 : row.getRetryCount() + 1;
        if (nextRetryCount >= orderTimeoutProperties.getOutbox().getMaxRetryCount()) {
            log.warn("Outbox publisher max retry failure traceId={} result=max_retry_failed messageId={} orderId={} retryCount={} lastError={}",
                    traceContext.currentTraceId(), row.getMessageId(), row.getOrderId(), nextRetryCount, truncate(e.getMessage()));
        }
    }

    private String truncate(String message) {
        String value = message == null ? "unknown publish failure" : message;
        if (value.length() <= LAST_ERROR_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
