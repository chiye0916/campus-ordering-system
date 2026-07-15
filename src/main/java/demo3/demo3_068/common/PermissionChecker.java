package demo3.demo3_068.common;

import demo3.demo3_068.exception.BusinessException;
import demo3.demo3_068.model.Role;

import java.util.Arrays;
import java.util.Set;

public class PermissionChecker {

    private PermissionChecker() {
    }

    public static Role currentRoleOrThrow() {
        Role role = BaseContext.getCurrentUserRole();
        if (role == null) {
            throw new BusinessException(401, "未登录");
        }
        return role;
    }

    public static void requireRole(Role role) {
        requireAnyRole(role);
    }

    public static void requireAnyRole(Role... roles) {
        Role currentRole = currentRoleOrThrow();
        Set<Role> allowedRoles = Set.copyOf(Arrays.asList(roles));
        if (!allowedRoles.contains(currentRole)) {
            throwPermissionDenied();
        }
    }

    public static void requireUser() {
        requireRole(Role.USER);
    }

    public static void requireMerchantOrAdmin() {
        requireAnyRole(Role.MERCHANT, Role.ADMIN);
    }

    public static void requireDeliveryOrAdmin() {
        requireAnyRole(Role.DELIVERY, Role.ADMIN);
    }

    public static void requireAdmin() {
        requireRole(Role.ADMIN);
    }

    public static void requireOwnerOrAdmin(Long ownerUserId) {
        Long userId = BaseContext.getCurrentUserId();
        Role role = currentRoleOrThrow();
        if (role == Role.ADMIN || ownerUserId != null && ownerUserId.equals(userId)) {
            return;
        }
        throwPermissionDenied();
    }

    public static void throwPermissionDenied() {
        throw new BusinessException(403, "无管理员权限");
    }
}
