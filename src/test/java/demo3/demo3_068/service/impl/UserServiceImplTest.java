package demo3.demo3_068.service.impl;

import demo3.demo3_068.common.Constants;
import demo3.demo3_068.dto.UserLoginDTO;
import demo3.demo3_068.dto.UserRegisterDTO;
import demo3.demo3_068.entity.User;
import demo3.demo3_068.exception.BusinessException;
import demo3.demo3_068.mapper.UserMapper;
import demo3.demo3_068.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceImplTest {

    @Test
    void systemUserCannotLoginThroughNormalApi() {
        UserMapper userMapper = mock(UserMapper.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserServiceImpl userService = new UserServiceImpl(
                userMapper,
                passwordEncoder,
                mock(JwtUtil.class),
                mock(StringRedisTemplate.class),
                mock(ObjectProvider.class),
                "");
        User systemUser = new User();
        systemUser.setId(999L);
        systemUser.setUsername("system_timeout");
        systemUser.setPassword("12345");
        systemUser.setRole(Constants.SYSTEM_USER_ROLE);
        when(userMapper.selectByUsername("system_timeout")).thenReturn(systemUser);

        UserLoginDTO dto = new UserLoginDTO();
        dto.setUsername("system_timeout");
        dto.setPassword("12345");

        assertThatThrownBy(() -> userService.login(dto))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(401);
        verify(passwordEncoder, never()).matches("12345", "12345");
    }

    @Test
    void publicRegistrationRejectsRoleInput() {
        UserMapper userMapper = mock(UserMapper.class);
        UserServiceImpl userService = new UserServiceImpl(
                userMapper,
                mock(PasswordEncoder.class),
                mock(JwtUtil.class),
                mock(StringRedisTemplate.class),
                mock(ObjectProvider.class),
                "");
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setUsername("merchant");
        dto.setEmail("merchant@example.com");
        dto.setPassword("123456");
        dto.setEmailCode("123456");
        dto.setRole("MERCHANT");

        assertThatThrownBy(() -> userService.register(dto))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);
        verify(userMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }
}
