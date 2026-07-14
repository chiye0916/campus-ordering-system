package demo3.demo3_068.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import demo3.demo3_068.config.DishCacheProperties;
import demo3.demo3_068.observability.BusinessMetrics;
import demo3.demo3_068.observability.TraceContext;
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
    private final BusinessMetrics businessMetrics;

    public DishListCacheService(StringRedisTemplate stringRedisTemplate,
                                ObjectMapper objectMapper,
                                DishCacheProperties properties,
                                BusinessMetrics businessMetrics) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.businessMetrics = businessMetrics;
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
            businessMetrics.recordDishListCache("error");
            log.warn("Dish-list cache operation failed traceId={} operation=read categoryId={} key={} message={}",
                    traceId(), categoryId, cacheKey, e.getMessage(), e);
            return Optional.empty();
        }

        if (cachedValue == null) {
            businessMetrics.recordDishListCache("miss");
            return Optional.empty();
        }

        try {
            businessMetrics.recordDishListCache("hit");
            return Optional.of(objectMapper.readValue(cachedValue, DISH_VO_LIST_TYPE));
        } catch (Exception e) {
            businessMetrics.recordDishListCache("error");
            log.warn("Dish-list cache operation failed traceId={} operation=corrupted-cache categoryId={} key={} message={}",
                    traceId(), categoryId, cacheKey, e.getMessage(), e);
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
            log.warn("Dish-list cache operation failed traceId={} operation=write categoryId={} key={} ttl={} message={}",
                    traceId(), categoryId, cacheKey, ttl, e.getMessage(), e);
        }
    }

    public void evict(Long categoryId, String operation) {
        String cacheKey = buildKey(categoryId);
        try {
            stringRedisTemplate.delete(cacheKey);
        } catch (Exception e) {
            log.warn("Dish-list cache operation failed traceId={} operation={} categoryId={} key={} message={}",
                    traceId(), operation, categoryId, cacheKey, e.getMessage(), e);
        }
    }

    public Duration selectTtl(List<DishVO> records) {
        return records.isEmpty() ? properties.getEmptyListTtl() : properties.getListTtl();
    }

    private String traceId() {
        return org.slf4j.MDC.get(TraceContext.TRACE_ID);
    }
}
