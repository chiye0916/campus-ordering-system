package demo3.demo3_068.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "order.timeout")
public class OrderTimeoutProperties {

    private Duration delay = Duration.ofMinutes(15);
    private Outbox outbox = new Outbox();
    private Listener listener = new Listener();

    @Data
    public static class Outbox {

        private Duration publishFixedDelay = Duration.ofSeconds(5);
        private int publishBatchSize = 20;
        private int maxRetryCount = 5;
        private Duration retryDelay = Duration.ofSeconds(30);
        private Duration publisherConfirmTimeout = Duration.ofSeconds(5);
        private Duration publisherClaimTimeout = Duration.ofMinutes(2);
    }

    @Data
    public static class Listener {

        private Retry retry = new Retry();

        @Data
        public static class Retry {

            private int maxAttempts = 3;
            private Duration initialInterval = Duration.ofSeconds(1);
            private double multiplier = 2.0;
            private Duration maxInterval = Duration.ofSeconds(10);
        }
    }
}
