package demo3.demo3_068.observability;

import demo3.demo3_068.mapper.OrderTimeoutOutboxMapper;
import demo3.demo3_068.model.OrderTimeoutOutboxStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class BusinessMetrics {

    static final Set<String> FORBIDDEN_TAG_KEYS = Set.of(
            "userId", "orderId", "tradeNo", "callbackNo", "messageId", "token", "payload");

    private final MeterRegistry meterRegistry;
    private final OrderTimeoutOutboxMapper orderTimeoutOutboxMapper;

    public BusinessMetrics(MeterRegistry meterRegistry, OrderTimeoutOutboxMapper orderTimeoutOutboxMapper) {
        this.meterRegistry = meterRegistry;
        this.orderTimeoutOutboxMapper = orderTimeoutOutboxMapper;
        registerOutboxGauges();
    }

    public void recordDishListCache(String result) {
        increment("dish.list.cache.requests", "result", result);
    }

    public void recordOrderSubmit(String result, String reason) {
        increment("order.submit.requests", "result", result, "reason", reason);
    }

    public void recordPaymentCallback(String result) {
        increment("payment.callback.requests", "result", result);
    }

    public void recordTimeoutCancel(String result) {
        increment("order.timeout.cancel.requests", "result", result);
    }

    public void increment(String metricName, String... keyValues) {
        try {
            validateLowCardinalityTags(keyValues);
            meterRegistry.counter(metricName, keyValues).increment();
        } catch (Exception e) {
            log.debug("Metric recording failed metricName={} message={}", metricName, e.getMessage(), e);
        }
    }

    private void registerOutboxGauges() {
        for (OrderTimeoutOutboxStatus status : OrderTimeoutOutboxStatus.values()) {
            Gauge.builder("order.timeout.outbox.records", () -> safeCountByStatus(status))
                    .tags(List.of(Tag.of("status", status.name().toLowerCase())))
                    .register(meterRegistry);
        }
    }

    private double safeCountByStatus(OrderTimeoutOutboxStatus status) {
        try {
            return orderTimeoutOutboxMapper.countByStatus(status.getCode());
        } catch (Exception e) {
            log.debug("Outbox gauge read failed status={} message={}", status, e.getMessage(), e);
            return 0;
        }
    }

    void validateLowCardinalityTags(String... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Metric tags must be key/value pairs");
        }
        for (int i = 0; i < keyValues.length; i += 2) {
            if (FORBIDDEN_TAG_KEYS.contains(keyValues[i])) {
                throw new IllegalArgumentException("Forbidden high-cardinality metric tag: " + keyValues[i]);
            }
        }
    }
}
