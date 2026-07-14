package demo3.demo3_068.observability;

import demo3.demo3_068.mapper.OrderTimeoutOutboxMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class BusinessMetricsTest {

    @Test
    void rejectsForbiddenHighCardinalityTagKeys() {
        BusinessMetrics metrics = new BusinessMetrics(new SimpleMeterRegistry(), mock(OrderTimeoutOutboxMapper.class));

        assertThatThrownBy(() -> metrics.validateLowCardinalityTags("orderId", "101"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metrics.validateLowCardinalityTags("tradeNo", "T123"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metrics.validateLowCardinalityTags("token", "secret"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsStableLowCardinalityTagKeys() {
        BusinessMetrics metrics = new BusinessMetrics(new SimpleMeterRegistry(), mock(OrderTimeoutOutboxMapper.class));

        assertThatNoException().isThrownBy(() ->
                metrics.validateLowCardinalityTags("result", "success", "reason", "ok", "status", "pending"));
    }
}
