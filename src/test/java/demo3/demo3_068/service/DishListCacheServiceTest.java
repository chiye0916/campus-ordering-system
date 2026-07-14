package demo3.demo3_068.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo3.demo3_068.config.DishCacheProperties;
import demo3.demo3_068.observability.BusinessMetrics;
import demo3.demo3_068.vo.DishVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DishListCacheServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private BusinessMetrics businessMetrics;

    private ObjectMapper objectMapper;
    private DishCacheProperties properties;
    private DishListCacheService dishListCacheService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new DishCacheProperties();
        dishListCacheService = new DishListCacheService(stringRedisTemplate, objectMapper, properties, businessMetrics);
    }

    @Test
    void buildKeyUsesCategoryIdAndSelectsDefaultTtls() {
        assertThat(dishListCacheService.buildKey(10L)).isEqualTo("dish:list:category:10");
        assertThat(dishListCacheService.selectTtl(List.of(dishVO(1L, 10L)))).isEqualTo(Duration.ofMinutes(30));
        assertThat(dishListCacheService.selectTtl(List.of())).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void putUsesNormalTtlForNonEmptyListAndEmptyTtlForEmptyList() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        dishListCacheService.put(10L, List.of(dishVO(1L, 10L)));
        dishListCacheService.put(11L, List.of());

        verify(valueOperations).set(eq("dish:list:category:10"), any(String.class), eq(Duration.ofMinutes(30)));
        verify(valueOperations).set("dish:list:category:11", "[]", Duration.ofMinutes(5));
    }

    @Test
    void getReturnsCachedEmptyArrayAsCacheHit() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("dish:list:category:10")).thenReturn("[]");

        Optional<List<DishVO>> cached = dishListCacheService.get(10L);

        assertThat(cached).isPresent();
        assertThat(cached.get()).isEmpty();
    }

    @Test
    void getMissingKeyReturnsCacheMiss() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("dish:list:category:10")).thenReturn(null);

        Optional<List<DishVO>> cached = dishListCacheService.get(10L);

        assertThat(cached).isEmpty();
    }

    @Test
    void corruptedCachedJsonDeletesKeyAndReturnsCacheMiss() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("dish:list:category:10")).thenReturn("{bad-json");

        Optional<List<DishVO>> cached = dishListCacheService.get(10L);

        assertThat(cached).isEmpty();
        verify(stringRedisTemplate).delete("dish:list:category:10");
    }

    @Test
    void corruptedCachedJsonDeleteFailureStillReturnsCacheMiss() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("dish:list:category:10")).thenReturn("{bad-json");
        doThrow(new IllegalStateException("redis down")).when(stringRedisTemplate).delete("dish:list:category:10");

        Optional<List<DishVO>> cached = dishListCacheService.get(10L);

        assertThat(cached).isEmpty();
    }

    @Test
    void getFailureReturnsCacheMiss() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("dish:list:category:10")).thenThrow(new IllegalStateException("redis down"));

        Optional<List<DishVO>> cached = dishListCacheService.get(10L);

        assertThat(cached).isEmpty();
    }

    @Test
    void putFailureDoesNotThrow() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new IllegalStateException("redis down"))
                .when(valueOperations).set(eq("dish:list:category:10"), any(String.class), eq(Duration.ofMinutes(30)));

        dishListCacheService.put(10L, List.of(dishVO(1L, 10L)));
    }

    @Test
    void evictFailureDoesNotThrow() {
        doThrow(new IllegalStateException("redis down")).when(stringRedisTemplate).delete("dish:list:category:10");

        dishListCacheService.evict(10L, "test");
    }

    private DishVO dishVO(Long id, Long categoryId) {
        return DishVO.builder()
                .id(id)
                .categoryId(categoryId)
                .categoryName("热菜")
                .name("宫保鸡丁")
                .price(new BigDecimal("18.00"))
                .image("dish.jpg")
                .description("微辣")
                .status(1)
                .build();
    }
}
