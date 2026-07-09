package demo3.demo3_068.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import demo3.demo3_068.common.OrderTimeoutRabbitConstants;
import demo3.demo3_068.dto.OrderTimeoutMessageDTO;
import demo3.demo3_068.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class OrderTimeoutCancelListener {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutCancelListener.class);

    private final ObjectMapper objectMapper;
    private final OrderService orderService;

    public OrderTimeoutCancelListener(ObjectMapper objectMapper, OrderService orderService) {
        this.objectMapper = objectMapper;
        this.orderService = orderService;
    }

    @RabbitListener(queues = OrderTimeoutRabbitConstants.CANCEL_QUEUE)
    public void handle(Message message) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        OrderTimeoutMessageDTO timeoutMessage;
        try {
            timeoutMessage = objectMapper.readValue(payload, OrderTimeoutMessageDTO.class);
            validate(timeoutMessage);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("Dropping malformed order timeout message payload={}", payload, e);
            return;
        }

        log.info("Consuming order timeout message messageId={} orderId={}",
                timeoutMessage.getMessageId(), timeoutMessage.getOrderId());
        orderService.timeoutCancel(timeoutMessage.getOrderId(), timeoutMessage.getMessageId());
    }

    private void validate(OrderTimeoutMessageDTO message) {
        if (message.getOrderId() == null || message.getMessageId() == null || message.getMessageId().isBlank()) {
            throw new IllegalArgumentException("orderId and messageId are required");
        }
    }
}
