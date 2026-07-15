package demo3.demo3_068.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo3.demo3_068.exception.BusinessException;
import demo3.demo3_068.model.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    @Test
    void parseTokenSupportsMerchantAndDeliveryRoles() {
        JwtUtil jwtUtil = new JwtUtil(new ObjectMapper(), "test-secret", 60000);

        assertThat(jwtUtil.parseToken(jwtUtil.generateToken(1L, "merchant", "MERCHANT")).getRole())
                .isEqualTo(Role.MERCHANT);
        assertThat(jwtUtil.parseToken(jwtUtil.generateToken(2L, "delivery", "DELIVERY")).getRole())
                .isEqualTo(Role.DELIVERY);
    }

    @Test
    void parseTokenRejectsUnsupportedRole() {
        JwtUtil jwtUtil = new JwtUtil(new ObjectMapper(), "test-secret", 60000);
        String token = jwtUtil.generateToken(1L, "owner", "OWNER");

        assertThatThrownBy(() -> jwtUtil.parseToken(token))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(401);
    }
}
