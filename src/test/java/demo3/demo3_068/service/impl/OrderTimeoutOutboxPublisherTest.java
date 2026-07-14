package demo3.demo3_068.service.impl;

import demo3.demo3_068.config.OrderTimeoutProperties;
import demo3.demo3_068.entity.OrderTimeoutOutbox;
import demo3.demo3_068.mapper.OrderTimeoutOutboxMapper;
import demo3.demo3_068.model.OrderTimeoutOutboxStatus;
import demo3.demo3_068.observability.TraceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderTimeoutOutboxPublisherTest {

    private OrderTimeoutOutboxMapper mapper;
    private RabbitTemplate rabbitTemplate;
    private OrderTimeoutProperties properties;
    private OrderTimeoutOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        mapper = org.mockito.Mockito.mock(OrderTimeoutOutboxMapper.class);
        rabbitTemplate = org.mockito.Mockito.mock(RabbitTemplate.class);
        properties = new OrderTimeoutProperties();
        properties.getOutbox().setPublisherConfirmTimeout(Duration.ofMillis(20));
        properties.getOutbox().setRetryDelay(Duration.ofSeconds(30));
        publisher = new OrderTimeoutOutboxPublisher(mapper, rabbitTemplate, properties, new TraceContext());
    }

    @Test
    void confirmedPublishMarksRowSent() {
        OrderTimeoutOutbox row = outboxRow();
        when(mapper.selectDueRows(any(), eq(1), eq(4), eq(5), eq(20))).thenReturn(List.of(row));
        when(mapper.claimForPublishing(eq(1L), any(), eq(1), eq(4), eq(2), eq(5))).thenReturn(1);
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));

        publisher.publishDueMessages();

        verify(mapper).markSent(eq(1L), any(), eq(OrderTimeoutOutboxStatus.PUBLISHING.getCode()), eq(OrderTimeoutOutboxStatus.SENT.getCode()));
    }

    @Test
    void nackPublishMarksRowFailedForRetry() {
        OrderTimeoutOutbox row = outboxRow();
        when(mapper.selectDueRows(any(), eq(1), eq(4), eq(5), eq(20))).thenReturn(List.of(row));
        when(mapper.claimForPublishing(eq(1L), any(), eq(1), eq(4), eq(2), eq(5))).thenReturn(1);
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(false, "nack"));
            return null;
        }).when(rabbitTemplate).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));

        publisher.publishDueMessages();

        verify(mapper, never()).markSent(any(), any(), any(), any());
        verify(mapper).markPublishFailed(eq(1L), org.mockito.ArgumentMatchers.contains("nack"), any(), any(), eq(2), eq(4));
    }

    @Test
    void unclaimedRowIsNotPublished() {
        OrderTimeoutOutbox row = outboxRow();
        when(mapper.selectDueRows(any(), eq(1), eq(4), eq(5), eq(20))).thenReturn(List.of(row));
        when(mapper.claimForPublishing(eq(1L), any(), eq(1), eq(4), eq(2), eq(5))).thenReturn(0);

        publisher.publishDueMessages();

        verify(rabbitTemplate, never()).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void stalePublishingRowsAreRecovered() {
        publisher.recoverStalePublishingRows();

        ArgumentCaptor<LocalDateTime> deadlineCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).recoverStalePublishing(
                any(),
                deadlineCaptor.capture(),
                any(),
                eq(OrderTimeoutOutboxStatus.PUBLISHING.getCode()),
                eq(OrderTimeoutOutboxStatus.FAILED.getCode()),
                eq("Publishing claim timed out"),
                eq(5));
        assertThat(deadlineCaptor.getValue()).isBefore(LocalDateTime.now());
    }

    private OrderTimeoutOutbox outboxRow() {
        OrderTimeoutOutbox row = new OrderTimeoutOutbox();
        row.setId(1L);
        row.setOrderId(101L);
        row.setMessageId("message-1");
        row.setPayload("{\"orderId\":101,\"messageId\":\"message-1\"}");
        return row;
    }
}
