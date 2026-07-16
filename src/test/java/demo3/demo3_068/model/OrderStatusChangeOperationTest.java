package demo3.demo3_068.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusChangeOperationTest {

    @Test
    void operationsExposeDefaultChineseReasonText() {
        assertThat(OrderStatusChangeOperation.ORDER_SUBMIT.getDefaultReason()).isEqualTo("订单创建，进入待支付状态");
        assertThat(OrderStatusChangeOperation.PAYMENT_SUCCESS.getDefaultReason()).isEqualTo("支付成功确认订单状态");
        assertThat(OrderStatusChangeOperation.USER_CANCEL.getDefaultReason()).isEqualTo("用户取消待支付订单");
        assertThat(OrderStatusChangeOperation.TIMEOUT_CANCEL.getDefaultReason()).isEqualTo("订单超时自动取消");
        assertThat(OrderStatusChangeOperation.MERCHANT_ACCEPT.getDefaultReason()).isEqualTo("商家接单");
        assertThat(OrderStatusChangeOperation.DELIVERY_START.getDefaultReason()).isEqualTo("配送员开始配送");
        assertThat(OrderStatusChangeOperation.DELIVERY_COMPLETE.getDefaultReason()).isEqualTo("配送员完成订单");
        assertThat(OrderStatusChangeOperation.REFUND_REQUEST_APPROVE.getDefaultReason()).isEqualTo("退款申请审核通过");
        assertThat(OrderStatusChangeOperation.REFUND_REQUEST_COMPLETE.getDefaultReason()).isEqualTo("退款申请完成退款");
        assertThat(OrderStatusChangeOperation.INTERNAL_REFUND_START.getDefaultReason()).isEqualTo("管理员内部发起模拟退款");
        assertThat(OrderStatusChangeOperation.INTERNAL_REFUND_COMPLETE.getDefaultReason()).isEqualTo("管理员内部完成模拟退款");
    }
}
