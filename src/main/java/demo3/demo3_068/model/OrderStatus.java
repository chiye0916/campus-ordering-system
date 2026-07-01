package demo3.demo3_068.model;

import demo3.demo3_068.exception.BusinessException;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum OrderStatus {

    PENDING_PAYMENT(1, "待支付"),
    PAID(2, "已支付"),
    COMPLETED(3, "已完成"),
    CANCELLED(4, "已取消"),
    ACCEPTED(5, "已接单"),
    DELIVERING(6, "配送中"),
    REFUNDING(7, "模拟退款中"),
    REFUNDED(8, "模拟已退款");

    private static final Map<Integer, OrderStatus> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(OrderStatus::getCode, Function.identity()));

    private final int code;
    private final String label;
    private Set<OrderStatus> nextStatuses = Set.of();

    static {
        PENDING_PAYMENT.nextStatuses = Set.of(PAID, CANCELLED);
        PAID.nextStatuses = Set.of(ACCEPTED, REFUNDING);
        ACCEPTED.nextStatuses = Set.of(DELIVERING, REFUNDING);
        DELIVERING.nextStatuses = Set.of(COMPLETED);
        REFUNDING.nextStatuses = Set.of(REFUNDED);
    }

    OrderStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public boolean canTransitionTo(OrderStatus target) {
        return nextStatuses.contains(target);
    }

    public void requireTransitionTo(OrderStatus target) {
        if (!canTransitionTo(target)) {
            throw new BusinessException("订单状态不能从" + label + "变更为" + target.label);
        }
    }

    public static OrderStatus fromCode(Integer code) {
        OrderStatus status = BY_CODE.get(code);
        if (status == null) {
            throw new BusinessException("未知订单状态：" + code);
        }
        return status;
    }
}
