package demo3.demo3_068.model;

import java.util.Set;

public enum PaymentStatus {

    PAYING(1),
    SUCCESS(2),
    FAILED(3),
    CLOSED(4);

    private static final Set<Integer> TERMINAL_CODES = Set.of(
            SUCCESS.code,
            FAILED.code,
            CLOSED.code);

    private final int code;

    PaymentStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static boolean isTerminal(Integer code) {
        return TERMINAL_CODES.contains(code);
    }
}
