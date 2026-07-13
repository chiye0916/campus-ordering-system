package demo3.demo3_068.model;

import java.util.Set;

public final class MockPayStatus {

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    private static final Set<String> SUPPORTED = Set.of(SUCCESS, FAILED);

    private MockPayStatus() {
    }

    public static boolean isSupported(String status) {
        return SUPPORTED.contains(status);
    }
}
