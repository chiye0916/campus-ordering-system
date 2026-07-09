package demo3.demo3_068.common;

public class OrderTimeoutRabbitConstants {

    public static final String DELAY_EXCHANGE = "order.timeout.delay.exchange";
    public static final String DEAD_EXCHANGE = "order.timeout.dead.exchange";
    public static final String DELAY_QUEUE = "order.timeout.delay.queue";
    public static final String CANCEL_QUEUE = "order.timeout.cancel.queue";
    public static final String DELAY_ROUTING_KEY = "order.timeout.delay";
    public static final String CANCEL_ROUTING_KEY = "order.timeout.cancel";

    private OrderTimeoutRabbitConstants() {
    }
}
