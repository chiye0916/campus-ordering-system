package demo3.demo3_068.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public class RefundNoUtil {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private RefundNoUtil() {
    }

    public static String generateRefundNo() {
        int suffix = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return "RF" + LocalDateTime.now().format(FORMATTER) + suffix;
    }
}
