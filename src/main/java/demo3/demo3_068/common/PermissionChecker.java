package demo3.demo3_068.common;

import demo3.demo3_068.exception.BusinessException;

public class PermissionChecker {

    private PermissionChecker() {
    }

    public static void requireAdmin() {
        String role = BaseContext.getCurrentUserRole();
        if (!Constants.ADMIN_USER_ROLE.equals(role)) {
            throw new BusinessException(403, "无管理员权限");
        }
    }
}
