package demo3.demo3_068.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo3.demo3_068.config.OrderTimeoutProperties;
import demo3.demo3_068.dto.OrderTimeoutMessageDTO;
import demo3.demo3_068.entity.OrderTimeoutOutbox;
import demo3.demo3_068.mapper.OrderTimeoutOutboxMapper;
import demo3.demo3_068.model.OrderTimeoutOutboxStatus;
import demo3.demo3_068.observability.TraceContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderTimeoutOutboxServiceImplTest {

    @Test
    void createPendingForOrderStoresPayloadStatusRetryAndExpireTime() throws Exception {
        OrderTimeoutOutboxMapper mapper = mock(OrderTimeoutOutboxMapper.class);
        OrderTimeoutProperties properties = new OrderTimeoutProperties();
        properties.setDelay(Duration.ofMinutes(15));
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        OrderTimeoutOutboxServiceImpl service = new OrderTimeoutOutboxServiceImpl(mapper, properties, objectMapper, new TraceContext());

        service.createPendingForOrder(101L);

        ArgumentCaptor<OrderTimeoutOutbox> captor = ArgumentCaptor.forClass(OrderTimeoutOutbox.class);
        verify(mapper).insert(captor.capture());
        OrderTimeoutOutbox outbox = captor.getValue();
        assertThat(outbox.getOrderId()).isEqualTo(101L);
        assertThat(outbox.getMessageId()).isNotBlank();
        assertThat(outbox.getStatus()).isEqualTo(OrderTimeoutOutboxStatus.PENDING.getCode());
        assertThat(outbox.getRetryCount()).isZero();
        assertThat(outbox.getNextRetryTime()).isNotNull();
        assertThat(outbox.getExpireTime()).isAfter(outbox.getCreateTime().plusMinutes(14));

        OrderTimeoutMessageDTO payload = objectMapper.readValue(outbox.getPayload(), OrderTimeoutMessageDTO.class);
        assertThat(payload.getOrderId()).isEqualTo(101L);
        assertThat(payload.getMessageId()).isEqualTo(outbox.getMessageId());
        assertThat(payload.getExpireTime()).isEqualTo(outbox.getExpireTime());
    }
}
