package demo3.demo3_068.model;

import demo3.demo3_068.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatusTest {

    @Test
    void mapsStatusCodesAndLabels() {
        assertThat(OrderStatus.fromCode(1)).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(OrderStatus.fromCode(2)).isEqualTo(OrderStatus.PAID);
        assertThat(OrderStatus.fromCode(3)).isEqualTo(OrderStatus.COMPLETED);
        assertThat(OrderStatus.fromCode(4)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(OrderStatus.fromCode(5)).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(OrderStatus.fromCode(6)).isEqualTo(OrderStatus.DELIVERING);
        assertThat(OrderStatus.fromCode(7)).isEqualTo(OrderStatus.REFUNDING);
        assertThat(OrderStatus.fromCode(8)).isEqualTo(OrderStatus.REFUNDED);
        assertThat(OrderStatus.REFUNDING.getLabel()).isEqualTo("模拟退款中");
    }

    @Test
    void allowsConfiguredTransitions() {
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.PAID)).isTrue();
        assertThat(OrderStatus.PENDING_PAYMENT.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.ACCEPTED)).isTrue();
        assertThat(OrderStatus.ACCEPTED.canTransitionTo(OrderStatus.DELIVERING)).isTrue();
        assertThat(OrderStatus.DELIVERING.canTransitionTo(OrderStatus.COMPLETED)).isTrue();
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.REFUNDING)).isTrue();
        assertThat(OrderStatus.ACCEPTED.canTransitionTo(OrderStatus.REFUNDING)).isTrue();
        assertThat(OrderStatus.REFUNDING.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
    }

    @Test
    void rejectsIllegalTransitionsAndUnknownCodes() {
        assertThat(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.PAID)).isFalse();
        assertThat(OrderStatus.COMPLETED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.ACCEPTED.canTransitionTo(OrderStatus.ACCEPTED)).isFalse();

        assertThatThrownBy(() -> OrderStatus.fromCode(99))
                .isInstanceOf(BusinessException.class)
                .hasMessage("未知订单状态：99");
    }

    @Test
    void rejectsTransitionsThatNumericOrderingWouldMisread() {
        // Numeric status order is not lifecycle order: code 3 is COMPLETED while 5 and 6 are earlier lifecycle states.
        assertThat(OrderStatus.PAID.getCode()).isLessThan(OrderStatus.COMPLETED.getCode());
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.COMPLETED)).isFalse();
        assertThat(OrderStatus.DELIVERING.canTransitionTo(OrderStatus.REFUNDING)).isFalse();
        assertThat(OrderStatus.COMPLETED.canTransitionTo(OrderStatus.REFUNDING)).isFalse();

        assertThatThrownBy(() -> OrderStatus.PAID.requireTransitionTo(OrderStatus.COMPLETED))
                .isInstanceOf(BusinessException.class);
    }
}
