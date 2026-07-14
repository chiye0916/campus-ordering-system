package demo3.demo3_068.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import demo3.demo3_068.config.OrderTimeoutProperties;
import demo3.demo3_068.dto.OrderTimeoutMessageDTO;
import demo3.demo3_068.entity.OrderTimeoutOutbox;
import demo3.demo3_068.exception.BusinessException;
import demo3.demo3_068.mapper.OrderTimeoutOutboxMapper;
import demo3.demo3_068.model.OrderTimeoutOutboxStatus;
import demo3.demo3_068.observability.TraceContext;
import demo3.demo3_068.service.OrderTimeoutOutboxService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class OrderTimeoutOutboxServiceImpl implements OrderTimeoutOutboxService {

    private final OrderTimeoutOutboxMapper orderTimeoutOutboxMapper;
    private final OrderTimeoutProperties orderTimeoutProperties;
    private final ObjectMapper objectMapper;
    private final TraceContext traceContext;

    public OrderTimeoutOutboxServiceImpl(OrderTimeoutOutboxMapper orderTimeoutOutboxMapper,
                                         OrderTimeoutProperties orderTimeoutProperties,
                                         ObjectMapper objectMapper,
                                         TraceContext traceContext) {
        this.orderTimeoutOutboxMapper = orderTimeoutOutboxMapper;
        this.orderTimeoutProperties = orderTimeoutProperties;
        this.objectMapper = objectMapper;
        this.traceContext = traceContext;
    }

    @Override
    public void createPendingForOrder(Long orderId) {
        LocalDateTime now = LocalDateTime.now();
        String messageId = UUID.randomUUID().toString();
        LocalDateTime expireTime = now.plus(orderTimeoutProperties.getDelay());
        OrderTimeoutMessageDTO payload = new OrderTimeoutMessageDTO(orderId, messageId, expireTime);

        OrderTimeoutOutbox outbox = new OrderTimeoutOutbox();
        outbox.setOrderId(orderId);
        outbox.setMessageId(messageId);
        outbox.setTraceId(traceContext.currentTraceId());
        outbox.setPayload(toJson(payload));
        outbox.setExpireTime(expireTime);
        outbox.setStatus(OrderTimeoutOutboxStatus.PENDING.getCode());
        outbox.setRetryCount(0);
        outbox.setNextRetryTime(now);
        outbox.setCreateTime(now);
        outbox.setUpdateTime(now);
        orderTimeoutOutboxMapper.insert(outbox);
    }

    private String toJson(OrderTimeoutMessageDTO payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BusinessException("创建订单超时消息失败");
        }
    }
}
