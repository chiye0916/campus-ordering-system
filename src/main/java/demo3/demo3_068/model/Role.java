package demo3.demo3_068.model;

import demo3.demo3_068.exception.BusinessException;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum Role {

    USER,
    MERCHANT,
    DELIVERY,
    ADMIN,
    SYSTEM;

    private static final Map<String, Role> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(Role::name, Function.identity()));

    public static Role parseForAuthentication(String value) {
        Role role = parse(value);
        if (role == null) {
            throw new BusinessException(401, "不支持的用户角色");
        }
        return role;
    }

    public static Role parseForPermission(String value) {
        Role role = parse(value);
        if (role == null) {
            throw new BusinessException(403, "无管理员权限");
        }
        return role;
    }

    private static Role parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return BY_NAME.get(value.trim().toUpperCase());
    }
}
