package demo3.demo3_068.common;

import demo3.demo3_068.exception.BusinessException;
import demo3.demo3_068.model.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionCheckerTest {

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void merchantOrAdminAllowsMerchantAndAdminOnly() {
        BaseContext.setCurrentUserId(1L);
        BaseContext.setCurrentUserRole(Role.MERCHANT);
        PermissionChecker.requireMerchantOrAdmin();

        BaseContext.setCurrentUserRole(Role.ADMIN);
        PermissionChecker.requireMerchantOrAdmin();

        BaseContext.setCurrentUserRole(Role.DELIVERY);
        assertThatThrownBy(PermissionChecker::requireMerchantOrAdmin)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);
    }

    @Test
    void systemIsDeniedByNormalEndpointHelpers() {
        BaseContext.setCurrentUserId(1L);
        BaseContext.setCurrentUserRole(Role.SYSTEM);

        assertThatThrownBy(PermissionChecker::requireUser)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);
        assertThatThrownBy(PermissionChecker::requireAdmin)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);
    }

    @Test
    void unsupportedRoleStringFailsClosed() {
        assertThatThrownBy(() -> BaseContext.setCurrentUserRole("OWNER"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);
    }
}
