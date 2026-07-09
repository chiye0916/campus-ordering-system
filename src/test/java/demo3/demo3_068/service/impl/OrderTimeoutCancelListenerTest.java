package demo3.demo3_068.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo3.demo3_068.dto.OrderTimeoutMessageDTO;
import demo3.demo3_068.exception.BusinessException;
import demo3.demo3_068.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OrderTimeoutCancelListenerTest {

    @Test
    void validMessageDelegatesToOrderService() throws Exception {
        OrderService orderService = mock(OrderService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        OrderTimeoutCancelListener listener = new OrderTimeoutCancelListener(objectMapper, orderService);
        String payload = objectMapper.writeValueAsString(
                new OrderTimeoutMessageDTO(101L, "message-1", LocalDateTime.now()));

        listener.handle(message(payload));

        verify(orderService).timeoutCancel(101L, "message-1");
    }

    @Test
    void malformedMessageIsDroppedWithoutRetry() {
        OrderService orderService = mock(OrderService.class);
        OrderTimeoutCancelListener listener = new OrderTimeoutCancelListener(new ObjectMapper(), orderService);

        listener.handle(message("{bad json"));

        verify(orderService, never()).timeoutCancel(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void technicalFailurePropagatesForRetry() throws Exception {
        OrderService orderService = mock(OrderService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        OrderTimeoutCancelListener listener = new OrderTimeoutCancelListener(objectMapper, orderService);
        String payload = objectMapper.writeValueAsString(
                new OrderTimeoutMessageDTO(101L, "message-1", LocalDateTime.now()));
        doThrow(new BusinessException("订单处理中，请稍后重试"))
                .when(orderService).timeoutCancel(101L, "message-1");

        assertThatThrownBy(() -> listener.handle(message(payload)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单处理中，请稍后重试");
    }

    private Message message(String payload) {
        return new Message(payload.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
