package demo3.demo3_068.model;

import demo3.demo3_068.exception.BusinessException;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum RefundRequestStatus {

    PENDING_REVIEW,
    APPROVED,
    REJECTED,
    COMPLETED;

    private static final Map<String, RefundRequestStatus> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(RefundRequestStatus::name, Function.identity()));

    public static RefundRequestStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        RefundRequestStatus status = BY_NAME.get(value.trim().toUpperCase());
        if (status == null) {
            throw new BusinessException(400, "退款申请状态不合法");
        }
        return status;
    }
}
