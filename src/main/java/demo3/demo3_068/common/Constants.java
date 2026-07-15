package demo3.demo3_068.common;

import demo3.demo3_068.model.Role;

public class Constants {

    public static final String LOGIN_USER_KEY_PREFIX = "login:user:";
    public static final String REGISTER_EMAIL_CODE_KEY_PREFIX = "register:email:code:";
    public static final String REGISTER_EMAIL_COOLDOWN_KEY_PREFIX = "register:email:cooldown:";
    public static final String ORDER_STATUS_LOCK_KEY_PREFIX = "lock:order:status:";
    public static final String ORDER_STATUS_LOCK_FAILED_MESSAGE = "订单处理中，请稍后重试";
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String DEFAULT_USER_ROLE = Role.USER.name();
    public static final String ADMIN_USER_ROLE = Role.ADMIN.name();
    public static final String MERCHANT_USER_ROLE = Role.MERCHANT.name();
    public static final String DELIVERY_USER_ROLE = Role.DELIVERY.name();
    public static final String SYSTEM_USER_ROLE = Role.SYSTEM.name();
    public static final String SYSTEM_TIMEOUT_USERNAME = "system_timeout";

    private Constants() {
    }
}
