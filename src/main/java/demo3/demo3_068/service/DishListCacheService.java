package demo3.demo3_068.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import demo3.demo3_068.config.DishCacheProperties;
import demo3.demo3_068.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class DishListCacheService {

    private static final String DISH_LIST_CACHE_KEY_PREFIX = "dish:list:category:";
    private static final TypeReference<List<DishVO>> DISH_VO_LIST_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final DishCacheProperties properties;

    public DishListCacheService(StringRedisTemplate stringRedisTemplate,
                                ObjectMapper objectMapper,
                                DishCacheProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public String buildKey(Long categoryId) {
        return DISH_LIST_CACHE_KEY_PREFIX + categoryId;
    }

    public Optional<List<DishVO>> get(Long categoryId) {
        String cacheKey = buildKey(categoryId);
        String cachedValue;
        try {
            cachedValue = stringRedisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("Read dish-list cache failed, categoryId={}, key={}, message={}",
                    categoryId, cacheKey, e.getMessage(), e);
            return Optional.empty();
        }

        if (cachedValue == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(cachedValue, DISH_VO_LIST_TYPE));
        } catch (Exception e) {
            log.warn("Dish-list cache contains invalid JSON, categoryId={}, key={}, message={}",
                    categoryId, cacheKey, e.getMessage(), e);
            evict(categoryId, "corrupted-cache");
            return Optional.empty();
        }
    }

    public void put(Long categoryId, List<DishVO> records) {
        String cacheKey = buildKey(categoryId);
        Duration ttl = selectTtl(records);
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(records), ttl);
        } catch (Exception e) {
            log.warn("Write dish-list cache failed, categoryId={}, key={}, ttl={}, message={}",
                    categoryId, cacheKey, ttl, e.getMessage(), e);
        }
    }

    public void evict(Long categoryId, String operation) {
        String cacheKey = buildKey(categoryId);
        try {
            stringRedisTemplate.delete(cacheKey);
        } catch (Exception e) {
            log.warn("Evict dish-list cache failed, operation={}, categoryId={}, key={}, message={}",
                    operation, categoryId, cacheKey, e.getMessage(), e);
        }
    }

    public Duration selectTtl(List<DishVO> records) {
        return records.isEmpty() ? properties.getEmptyListTtl() : properties.getListTtl();
    }
}
