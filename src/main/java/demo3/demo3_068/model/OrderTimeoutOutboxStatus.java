package demo3.demo3_068.model;

import demo3.demo3_068.exception.BusinessException;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum OrderTimeoutOutboxStatus {

    PENDING(1),
    PUBLISHING(2),
    SENT(3),
    FAILED(4);

    private static final Map<Integer, OrderTimeoutOutboxStatus> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(OrderTimeoutOutboxStatus::getCode, Function.identity()));

    private final int code;

    OrderTimeoutOutboxStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static OrderTimeoutOutboxStatus fromCode(Integer code) {
        OrderTimeoutOutboxStatus status = BY_CODE.get(code);
        if (status == null) {
            throw new BusinessException("未知订单超时 outbox 状态：" + code);
        }
        return status;
    }
}
