package demo3.demo3_068.model;

import demo3.demo3_068.exception.BusinessException;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum OrderStatusChangeOperation {

    ORDER_SUBMIT("订单创建，进入待支付状态"),
    PAYMENT_SUCCESS("支付成功确认订单状态"),
    USER_CANCEL("用户取消待支付订单"),
    TIMEOUT_CANCEL("订单超时自动取消"),
    MERCHANT_ACCEPT("商家接单"),
    DELIVERY_START("配送员开始配送"),
    DELIVERY_COMPLETE("配送员完成订单"),
    REFUND_REQUEST_APPROVE("退款申请审核通过"),
    REFUND_REQUEST_COMPLETE("退款申请完成退款"),
    INTERNAL_REFUND_START("管理员内部发起模拟退款"),
    INTERNAL_REFUND_COMPLETE("管理员内部完成模拟退款");

    private static final Map<String, OrderStatusChangeOperation> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(OrderStatusChangeOperation::name, Function.identity()));

    private final String defaultReason;

    OrderStatusChangeOperation(String defaultReason) {
        this.defaultReason = defaultReason;
    }

    public String getDefaultReason() {
        return defaultReason;
    }

    public static OrderStatusChangeOperation parse(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("未知订单状态变更操作：" + value);
        }
        OrderStatusChangeOperation operation = BY_NAME.get(value.trim().toUpperCase());
        if (operation == null) {
            throw new BusinessException("未知订单状态变更操作：" + value);
        }
        return operation;
    }
}
