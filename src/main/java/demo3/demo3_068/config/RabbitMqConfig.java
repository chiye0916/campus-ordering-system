package demo3.demo3_068.config;

import demo3.demo3_068.common.OrderTimeoutRabbitConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

import java.util.Map;

@Configuration
@EnableConfigurationProperties(OrderTimeoutProperties.class)
public class RabbitMqConfig {

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        rabbitTemplate.setMandatory(true);
        return rabbitTemplate;
    }

    @Bean
    public DirectExchange orderTimeoutDelayExchange() {
        return new DirectExchange(OrderTimeoutRabbitConstants.DELAY_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange orderTimeoutDeadExchange() {
        return new DirectExchange(OrderTimeoutRabbitConstants.DEAD_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderTimeoutDelayQueue(OrderTimeoutProperties properties) {
        return new Queue(
                OrderTimeoutRabbitConstants.DELAY_QUEUE,
                true,
                false,
                false,
                Map.of(
                        "x-message-ttl", properties.getDelay().toMillis(),
                        "x-dead-letter-exchange", OrderTimeoutRabbitConstants.DEAD_EXCHANGE,
                        "x-dead-letter-routing-key", OrderTimeoutRabbitConstants.CANCEL_ROUTING_KEY));
    }

    @Bean
    public Queue orderTimeoutCancelQueue() {
        return new Queue(OrderTimeoutRabbitConstants.CANCEL_QUEUE, true);
    }

    @Bean
    public Binding orderTimeoutDelayBinding(Queue orderTimeoutDelayQueue, DirectExchange orderTimeoutDelayExchange) {
        return BindingBuilder.bind(orderTimeoutDelayQueue)
                .to(orderTimeoutDelayExchange)
                .with(OrderTimeoutRabbitConstants.DELAY_ROUTING_KEY);
    }

    @Bean
    public Binding orderTimeoutCancelBinding(Queue orderTimeoutCancelQueue, DirectExchange orderTimeoutDeadExchange) {
        return BindingBuilder.bind(orderTimeoutCancelQueue)
                .to(orderTimeoutDeadExchange)
                .with(OrderTimeoutRabbitConstants.CANCEL_ROUTING_KEY);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            RetryOperationsInterceptor orderTimeoutRetryInterceptor,
            @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAdviceChain(orderTimeoutRetryInterceptor);
        factory.setDefaultRequeueRejected(false);
        factory.setAutoStartup(autoStartup);
        return factory;
    }

    @Bean
    public RetryOperationsInterceptor orderTimeoutRetryInterceptor(OrderTimeoutProperties properties) {
        OrderTimeoutProperties.Listener.Retry retry = properties.getListener().getRetry();
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(retry.getMaxAttempts())
                .backOffOptions(
                        retry.getInitialInterval().toMillis(),
                        retry.getMultiplier(),
                        retry.getMaxInterval().toMillis())
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }
}
