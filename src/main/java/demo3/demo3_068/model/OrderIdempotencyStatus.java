package demo3.demo3_068.model;

import demo3.demo3_068.exception.BusinessException;

import java.util.Arrays;

public enum OrderIdempotencyStatus {

    PROCESSING(1, "处理中"),
    SUCCEEDED(2, "已成功"),
    FAILED(3, "已失败");

    private final int code;
    private final String label;

    OrderIdempotencyStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static OrderIdempotencyStatus fromCode(Integer code) {
        return Arrays.stream(values())
                .filter(status -> Integer.valueOf(status.code).equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException("未知下单幂等状态：" + code));
    }
}
