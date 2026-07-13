package demo3.demo3_068.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DishListCacheIT extends BaseIntegrationTest {

    @Test
    void cacheMissWritesRedisWithPositiveTtl() throws Exception {
        createUser("user_cache_miss", "USER");
        String token = login("user_cache_miss");
        Long categoryId = createCategory("Rice");
        Long dishId = createDish(categoryId, "Beef Rice", "18.50", 1);

        JsonNode response = getJsonWithToken("/dish/list?categoryId=" + categoryId, token);

        assertThat(response.path("code").asInt()).isEqualTo(200);
        assertThat(response.path("data")).hasSize(1);
        assertThat(response.path("data").get(0).path("id").asLong()).isEqualTo(dishId);

        String key = dishCacheKey(categoryId);
        assertThat(stringRedisTemplate.opsForValue().get(key)).contains("Beef Rice");
        assertThat(stringRedisTemplate.getExpire(key)).isPositive();
    }

    @Test
    void cacheHitReturnsCachedValueAfterCategoryValidation() throws Exception {
        createUser("user_cache_hit", "USER");
        String token = login("user_cache_hit");
        Long categoryId = createCategory("Noodles");
        stringRedisTemplate.opsForValue().set(dishCacheKey(categoryId), """
                [{"id":9981,"categoryId":%d,"categoryName":"Noodles","name":"Cached Noodle","price":9.90,"image":"cached.png","description":"from redis","status":1}]
                """.formatted(categoryId));

        JsonNode response = getJsonWithToken("/dish/list?categoryId=" + categoryId, token);

        assertThat(response.path("code").asInt()).isEqualTo(200);
        assertThat(response.path("data")).hasSize(1);
        assertThat(response.path("data").get(0).path("id").asLong()).isEqualTo(9981L);
        assertThat(response.path("data").get(0).path("name").asText()).isEqualTo("Cached Noodle");
        assertThat(longCell("select count(*) from dish")).isZero();
    }

    @Test
    void emptyListIsCachedAsJsonArray() throws Exception {
        createUser("user_empty_cache", "USER");
        String token = login("user_empty_cache");
        Long categoryId = createCategory("Soup");

        JsonNode response = getJsonWithToken("/dish/list?categoryId=" + categoryId, token);

        assertThat(response.path("code").asInt()).isEqualTo(200);
        assertThat(response.path("data")).isEmpty();
        assertThat(stringRedisTemplate.opsForValue().get(dishCacheKey(categoryId))).isEqualTo("[]");
        assertThat(stringRedisTemplate.getExpire(dishCacheKey(categoryId))).isPositive();
    }

    @Test
    void dishCreateInvalidatesAffectedCategoryCache() throws Exception {
        createUser("admin_cache_mutation", "ADMIN");
        String adminToken = login("admin_cache_mutation");
        Long categoryId = createCategory("Drinks");
        stringRedisTemplate.opsForValue().set(dishCacheKey(categoryId), "[]");

        JsonNode response = postJsonWithToken("/dish", adminToken, Map.of(
                "categoryId", categoryId,
                "name", "Orange Juice",
                "price", "8.00",
                "image", "juice.png",
                "description", "fresh",
                "status", 1));

        assertThat(response.path("code").asInt()).isEqualTo(200);
        assertThat(Boolean.TRUE.equals(stringRedisTemplate.hasKey(dishCacheKey(categoryId)))).isFalse();
    }
}
