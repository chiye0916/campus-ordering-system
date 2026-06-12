package demo3.demo3_068.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import demo3.demo3_068.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JwtUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final String secretKey;
    private final long ttlMillis;

    public JwtUtil(ObjectMapper objectMapper,
                   @Value("${jwt.secret-key}") String secretKey,
                   @Value("${jwt.ttl-millis}") long ttlMillis) {
        this.objectMapper = objectMapper;
        this.secretKey = secretKey;
        this.ttlMillis = ttlMillis;
    }

    public String generateToken(Long userId, String username, String role) {
        try {
            long now = Instant.now().toEpochMilli();
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userId", userId);
            payload.put("username", username);
            payload.put("role", role);
            payload.put("iat", now);
            payload.put("exp", now + ttlMillis);

            String headerPart = base64UrlEncode(objectMapper.writeValueAsBytes(header));
            String payloadPart = base64UrlEncode(objectMapper.writeValueAsBytes(payload));
            String unsignedToken = headerPart + "." + payloadPart;
            return unsignedToken + "." + sign(unsignedToken);
        } catch (Exception e) {
            throw new BusinessException(500, "生成token失败");
        }
    }

    public JwtClaims parseToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new BusinessException(401, "token格式错误");
            }

            String unsignedToken = parts[0] + "." + parts[1];
            String expectedSignature = sign(unsignedToken);
            if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8))) {
                throw new BusinessException(401, "token签名无效");
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> payload = objectMapper.readValue(payloadBytes, MAP_TYPE);
            long exp = ((Number) payload.get("exp")).longValue();
            if (Instant.now().toEpochMilli() > exp) {
                throw new BusinessException(401, "token已过期");
            }

            Long userId = ((Number) payload.get("userId")).longValue();
            String username = String.valueOf(payload.get("username"));
            String role = String.valueOf(payload.get("role"));
            return new JwtClaims(userId, username, role);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(401, "token解析失败");
        }
    }

    public long getTtlMillis() {
        return ttlMillis;
    }

    private String sign(String data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
        mac.init(secretKeySpec);
        return base64UrlEncode(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
