package demo3.demo3_068.common;

public class Constants {

    public static final String LOGIN_USER_KEY_PREFIX = "login:user:";
    public static final String REGISTER_EMAIL_CODE_KEY_PREFIX = "register:email:code:";
    public static final String REGISTER_EMAIL_COOLDOWN_KEY_PREFIX = "register:email:cooldown:";
    public static final String ORDER_STATUS_LOCK_KEY_PREFIX = "lock:order:status:";
    public static final String ORDER_STATUS_LOCK_FAILED_MESSAGE = "订单处理中，请稍后重试";
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String DEFAULT_USER_ROLE = "USER";
    public static final String ADMIN_USER_ROLE = "ADMIN";

    private Constants() {
    }
}
