package demo3.demo3_068.common;

import demo3.demo3_068.model.Role;

public class BaseContext {

    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Role> CURRENT_USER_ROLE = new ThreadLocal<>();

    private BaseContext() {
    }

    public static void setCurrentUserId(Long userId) {
        CURRENT_USER_ID.set(userId);
    }

    public static void setCurrentUserRole(Role role) {
        CURRENT_USER_ROLE.set(role);
    }

    public static void setCurrentUserRole(String role) {
        CURRENT_USER_ROLE.set(Role.parseForPermission(role));
    }

    public static Long getCurrentUserId() {
        return CURRENT_USER_ID.get();
    }

    public static Role getCurrentUserRole() {
        return CURRENT_USER_ROLE.get();
    }

    public static String getCurrentUserRoleName() {
        Role role = CURRENT_USER_ROLE.get();
        return role == null ? null : role.name();
    }

    public static void removeCurrentUserId() {
        CURRENT_USER_ID.remove();
    }

    public static void removeCurrentUserRole() {
        CURRENT_USER_ROLE.remove();
    }

    public static void clear() {
        removeCurrentUserId();
        removeCurrentUserRole();
    }
}
