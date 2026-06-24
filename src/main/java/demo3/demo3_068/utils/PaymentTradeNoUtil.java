package demo3.demo3_068.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public class PaymentTradeNoUtil {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private PaymentTradeNoUtil() {
    }

    public static String generateTradeNo() {
        int randomNumber = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "PAY" + LocalDateTime.now().format(FORMATTER) + randomNumber;
    }
}
