package demo3.demo3_068.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import demo3.demo3_068.common.OrderTimeoutRabbitConstants;
import demo3.demo3_068.dto.OrderTimeoutMessageDTO;
import demo3.demo3_068.observability.BusinessMetrics;
import demo3.demo3_068.observability.TraceContext;
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
    private final TraceContext traceContext;
    private final BusinessMetrics businessMetrics;

    public OrderTimeoutCancelListener(ObjectMapper objectMapper,
                                      OrderService orderService,
                                      TraceContext traceContext,
                                      BusinessMetrics businessMetrics) {
        this.objectMapper = objectMapper;
        this.orderService = orderService;
        this.traceContext = traceContext;
        this.businessMetrics = businessMetrics;
    }

    @RabbitListener(queues = OrderTimeoutRabbitConstants.CANCEL_QUEUE)
    public void handle(Message message) {
        try (TraceContext.Scope ignored = traceContext.open(traceHeader(message))) {
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            OrderTimeoutMessageDTO timeoutMessage;
            try {
                timeoutMessage = objectMapper.readValue(payload, OrderTimeoutMessageDTO.class);
                validate(timeoutMessage);
            } catch (JsonProcessingException | IllegalArgumentException e) {
                businessMetrics.recordTimeoutCancel("fail");
                log.warn("Timeout cancel listener outcome traceId={} result=fail reason=malformed_message payloadSummary={}",
                        traceContext.currentTraceId(), summarize(payload), e);
                return;
            }

            log.info("Timeout cancel listener received traceId={} messageId={} orderId={}",
                    traceContext.currentTraceId(), timeoutMessage.getMessageId(), timeoutMessage.getOrderId());
            try {
                orderService.timeoutCancel(timeoutMessage.getOrderId(), timeoutMessage.getMessageId());
            } catch (RuntimeException e) {
                businessMetrics.recordTimeoutCancel("fail");
                log.warn("Timeout cancel listener outcome traceId={} result=fail messageId={} orderId={} message={}",
                        traceContext.currentTraceId(), timeoutMessage.getMessageId(), timeoutMessage.getOrderId(), e.getMessage(), e);
                throw e;
            }
        }
    }

    private void validate(OrderTimeoutMessageDTO message) {
        if (message.getOrderId() == null || message.getMessageId() == null || message.getMessageId().isBlank()) {
            throw new IllegalArgumentException("orderId and messageId are required");
        }
    }

    private String traceHeader(Message message) {
        Object value = message.getMessageProperties().getHeaders().get(TraceContext.TRACE_HEADER);
        return value == null ? null : value.toString();
    }

    private String summarize(String payload) {
        if (payload == null) {
            return null;
        }
        return payload.length() <= 120 ? payload : payload.substring(0, 120);
    }
}
