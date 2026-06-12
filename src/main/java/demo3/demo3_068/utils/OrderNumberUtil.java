package demo3.demo3_068.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public class OrderNumberUtil {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private OrderNumberUtil() {
    }

    public static String generateOrderNumber() {
        int randomNumber = ThreadLocalRandom.current().nextInt(1000, 10000);
        return LocalDateTime.now().format(FORMATTER) + randomNumber;
    }
}
