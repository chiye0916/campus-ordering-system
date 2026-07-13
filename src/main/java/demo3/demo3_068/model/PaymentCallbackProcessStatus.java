package demo3.demo3_068.model;

import java.util.Set;

public enum PaymentCallbackProcessStatus {

    PROCESSING(1),
    PROCESSED(2),
    DUPLICATE(3),
    FAILED(4),
    IGNORED(5);

    private static final Set<Integer> TERMINAL_CODES = Set.of(
            PROCESSED.code,
            DUPLICATE.code,
            FAILED.code,
            IGNORED.code);

    private final int code;

    PaymentCallbackProcessStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static boolean isTerminal(Integer code) {
        return TERMINAL_CODES.contains(code);
    }
}
