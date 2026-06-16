package demo3.demo3_068.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo3.demo3_068.common.BaseContext;
import demo3.demo3_068.common.Constants;
import demo3.demo3_068.common.Result;
import demo3.demo3_068.exception.BusinessException;
import demo3.demo3_068.utils.JwtClaims;
import demo3.demo3_068.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtTokenInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public JwtTokenInterceptor(JwtUtil jwtUtil,
                               StringRedisTemplate stringRedisTemplate,
                               ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        try {
            String authorization = request.getHeader("Authorization");
            if (authorization == null || !authorization.startsWith(Constants.TOKEN_PREFIX)) {
                throw new BusinessException(401, "请先登录");
            }

            String token = authorization.substring(Constants.TOKEN_PREFIX.length());
            JwtClaims jwtClaims = jwtUtil.parseToken(token);
            String redisKey = Constants.LOGIN_USER_KEY_PREFIX + jwtClaims.getUserId();
            String redisToken = stringRedisTemplate.opsForValue().get(redisKey);
            if (!token.equals(redisToken)) {
                throw new BusinessException(401, "登录已失效");
            }

            BaseContext.setCurrentUserId(jwtClaims.getUserId());
            BaseContext.setCurrentUserRole(jwtClaims.getRole());
            return true;
        } catch (BusinessException e) {
            BaseContext.clear();
            writeError(response, e.getCode(), e.getMessage());
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        BaseContext.clear();
    }

    private void writeError(HttpServletResponse response, Integer code, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(code, message)));
    }
}
