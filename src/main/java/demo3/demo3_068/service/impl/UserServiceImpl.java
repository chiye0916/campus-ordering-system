package demo3.demo3_068.service.impl;

import demo3.demo3_068.common.BaseContext;
import demo3.demo3_068.common.Constants;
import demo3.demo3_068.dto.EmailCodeDTO;
import demo3.demo3_068.dto.UserLoginDTO;
import demo3.demo3_068.dto.UserRegisterDTO;
import demo3.demo3_068.entity.User;
import demo3.demo3_068.exception.BusinessException;
import demo3.demo3_068.mapper.UserMapper;
import demo3.demo3_068.model.Role;
import demo3.demo3_068.service.UserService;
import demo3.demo3_068.utils.JwtUtil;
import demo3.demo3_068.vo.UserLoginVO;
import demo3.demo3_068.vo.UserVO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectProvider<JavaMailSender> javaMailSenderProvider;
    private final String mailUsername;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserServiceImpl(UserMapper userMapper,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil,
                           StringRedisTemplate stringRedisTemplate,
                           ObjectProvider<JavaMailSender> javaMailSenderProvider,
                           @Value("${spring.mail.username:}") String mailUsername) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.stringRedisTemplate = stringRedisTemplate;
        this.javaMailSenderProvider = javaMailSenderProvider;
        this.mailUsername = mailUsername;
    }

    @Override
    public void sendEmailCode(EmailCodeDTO emailCodeDTO) {
        String email = emailCodeDTO.getEmail().trim();
        User existingUser = userMapper.selectByEmail(email);
        if (existingUser != null) {
            throw new BusinessException("邮箱已被注册");
        }

        String cooldownKey = Constants.REGISTER_EMAIL_COOLDOWN_KEY_PREFIX + email;
        Boolean inCooldown = stringRedisTemplate.hasKey(cooldownKey);
        if (Boolean.TRUE.equals(inCooldown)) {
            throw new BusinessException("验证码发送过于频繁，请稍后再试");
        }

        String code = generateEmailCode();
        sendRegisterCodeMail(email, code);

        String codeKey = Constants.REGISTER_EMAIL_CODE_KEY_PREFIX + email;
        stringRedisTemplate.opsForValue().set(codeKey, code, 5, TimeUnit.MINUTES);
        stringRedisTemplate.opsForValue().set(cooldownKey, "1", 60, TimeUnit.SECONDS);
    }

    @Override
    public Long register(UserRegisterDTO userRegisterDTO) {
        if (userRegisterDTO.getRole() != null && !userRegisterDTO.getRole().isBlank()) {
            throw new BusinessException(403, "无管理员权限");
        }
        String email = userRegisterDTO.getEmail().trim();
        User existingUser = userMapper.selectByUsername(userRegisterDTO.getUsername());
        if (existingUser != null) {
            throw new BusinessException("用户名已存在");
        }
        User existingEmailUser = userMapper.selectByEmail(email);
        if (existingEmailUser != null) {
            throw new BusinessException("邮箱已被注册");
        }
        verifyEmailCode(email, userRegisterDTO.getEmailCode());

        User user = new User();
        user.setUsername(userRegisterDTO.getUsername());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(userRegisterDTO.getPassword()));
        user.setNickname(userRegisterDTO.getNickname());
        user.setRole(Role.USER.name());
        userMapper.insert(user);
        stringRedisTemplate.delete(Constants.REGISTER_EMAIL_CODE_KEY_PREFIX + email);
        return user.getId();
    }

    @Override
    public UserLoginVO login(UserLoginDTO userLoginDTO) {
        User user = userMapper.selectByUsername(userLoginDTO.getUsername());
        if (user == null) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        Role role = Role.parseForAuthentication(user.getRole());
        if (role == Role.SYSTEM || !passwordEncoder.matches(userLoginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), role.name());
        String redisKey = Constants.LOGIN_USER_KEY_PREFIX + user.getId();
        stringRedisTemplate.opsForValue().set(redisKey, token, jwtUtil.getTtlMillis(), TimeUnit.MILLISECONDS);

        return UserLoginVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .role(role.name())
                .token(token)
                .build();
    }

    @Override
    public void logout() {
        Long userId = BaseContext.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        stringRedisTemplate.delete(Constants.LOGIN_USER_KEY_PREFIX + userId);
    }

    @Override
    public UserVO getCurrentUser() {
        Long userId = BaseContext.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .role(user.getRole())
                .build();
    }

    private String generateEmailCode() {
        return String.valueOf(secureRandom.nextInt(900000) + 100000);
    }

    private void sendRegisterCodeMail(String email, String code) {
        JavaMailSender javaMailSender = javaMailSenderProvider.getIfAvailable();
        if (javaMailSender == null || mailUsername == null || mailUsername.isBlank()) {
            throw new BusinessException("邮箱服务未配置");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailUsername);
        message.setTo(email);
        message.setSubject("Executive Dining 注册验证码");
        message.setText("""
                您正在注册 Executive Dining。

                验证码：%s

                该验证码 5 分钟内有效，请勿泄露给他人。
                """.formatted(code));
        javaMailSender.send(message);
    }

    private void verifyEmailCode(String email, String emailCode) {
        String codeKey = Constants.REGISTER_EMAIL_CODE_KEY_PREFIX + email;
        String cachedCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (cachedCode == null) {
            throw new BusinessException("邮箱验证码已过期，请重新获取");
        }
        if (!cachedCode.equals(emailCode)) {
            throw new BusinessException("邮箱验证码错误");
        }
    }
}
