package demo3.demo3_068.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import demo3.demo3_068.config.DishCacheProperties;
import demo3.demo3_068.dto.DishCreateDTO;
import demo3.demo3_068.dto.DishListQueryDTO;
import demo3.demo3_068.dto.DishStatusDTO;
import demo3.demo3_068.dto.DishUpdateDTO;
import demo3.demo3_068.entity.Category;
import demo3.demo3_068.entity.Dish;
import demo3.demo3_068.exception.BusinessException;
import demo3.demo3_068.mapper.CategoryMapper;
import demo3.demo3_068.mapper.DishMapper;
import demo3.demo3_068.service.DishListCacheService;
import demo3.demo3_068.vo.DishVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DishServiceImplTest {

    @Mock
    private DishMapper dishMapper;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private DishServiceImpl dishService;

    @BeforeEach
    void setUp() {
        DishListCacheService cacheService = new DishListCacheService(
                stringRedisTemplate,
                new ObjectMapper(),
                new DishCacheProperties());
        dishService = new DishServiceImpl(dishMapper, categoryMapper, cacheService);
    }

    @Test
    void listCacheHitReturnsCachedDataAndSkipsDishQuery() throws Exception {
        when(categoryMapper.selectById(10L)).thenReturn(category(10L));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("dish:list:category:10")).thenReturn(new ObjectMapper().writeValueAsString(List.of(dishVO(1L, 10L))));

        List<DishVO> records = dishService.list(listQuery(10L));

        assertThat(records).hasSize(1);
        assertThat(records.get(0).getName()).isEqualTo("宫保鸡丁");
        verify(dishMapper, never()).selectAvailableByCategoryId(any());
    }

    @Test
    void listCacheMissQueriesDatabaseAndWritesNormalTtl() {
        when(categoryMapper.selectById(10L)).thenReturn(category(10L));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("dish:list:category:10")).thenReturn(null);
        when(dishMapper.selectAvailableByCategoryId(10L)).thenReturn(List.of(dish(1L, 10L)));

        List<DishVO> records = dishService.list(listQuery(10L));

        assertThat(records).extracting(DishVO::getName).containsExactly("宫保鸡丁");
        verify(valueOperations).set(eq("dish:list:category:10"), any(String.class), eq(Duration.ofMinutes(30)));
    }

    @Test
    void listExistingCategoryWithNoDishesWritesEmptyArrayWithEmptyTtl() {
        when(categoryMapper.selectById(10L)).thenReturn(category(10L));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("dish:list:category:10")).thenReturn(null);
        when(dishMapper.selectAvailableByCategoryId(10L)).thenReturn(List.of());

        List<DishVO> records = dishService.list(listQuery(10L));

        assertThat(records).isEmpty();
        verify(valueOperations).set("dish:list:category:10", "[]", Duration.ofMinutes(5));
    }

    @Test
    void listRedisGetFailureFallsBackToDatabase() {
        when(categoryMapper.selectById(10L)).thenReturn(category(10L));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("dish:list:category:10")).thenThrow(new IllegalStateException("redis down"));
        when(dishMapper.selectAvailableByCategoryId(10L)).thenReturn(List.of(dish(1L, 10L)));

        List<DishVO> records = dishService.list(listQuery(10L));

        assertThat(records).hasSize(1);
        verify(dishMapper).selectAvailableByCategoryId(10L);
    }

    @Test
    void listRedisSetFailureStillReturnsDatabaseResult() {
        when(categoryMapper.selectById(10L)).thenReturn(category(10L));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("dish:list:category:10")).thenReturn(null);
        when(dishMapper.selectAvailableByCategoryId(10L)).thenReturn(List.of(dish(1L, 10L)));
        doThrow(new IllegalStateException("redis down"))
                .when(valueOperations).set(eq("dish:list:category:10"), any(String.class), eq(Duration.ofMinutes(30)));

        List<DishVO> records = dishService.list(listQuery(10L));

        assertThat(records).hasSize(1);
    }

    @Test
    void corruptedCacheFallsBackToDatabaseAndRewritesCache() {
        when(categoryMapper.selectById(10L)).thenReturn(category(10L));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("dish:list:category:10")).thenReturn("{bad-json");
        when(dishMapper.selectAvailableByCategoryId(10L)).thenReturn(List.of(dish(1L, 10L)));

        List<DishVO> records = dishService.list(listQuery(10L));

        assertThat(records).hasSize(1);
        verify(stringRedisTemplate).delete("dish:list:category:10");
        verify(valueOperations).set(eq("dish:list:category:10"), any(String.class), eq(Duration.ofMinutes(30)));
    }

    @Test
    void corruptedCacheDeleteFailureStillReturnsDatabaseResult() {
        when(categoryMapper.selectById(10L)).thenReturn(category(10L));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("dish:list:category:10")).thenReturn("{bad-json");
        doThrow(new IllegalStateException("redis down")).when(stringRedisTemplate).delete("dish:list:category:10");
        when(dishMapper.selectAvailableByCategoryId(10L)).thenReturn(List.of(dish(1L, 10L)));

        List<DishVO> records = dishService.list(listQuery(10L));

        assertThat(records).hasSize(1);
    }

    @Test
    void missingCategoryDoesNotReadOrWriteCache() {
        when(categoryMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> dishService.list(listQuery(404L)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("分类不存在");
        verifyNoInteractions(stringRedisTemplate);
        verify(dishMapper, never()).selectAvailableByCategoryId(any());
    }

    @Test
    void createEvictsNewCategory() {
        when(categoryMapper.selectById(10L)).thenReturn(category(10L));
        when(dishMapper.selectByName("宫保鸡丁")).thenReturn(null);

        dishService.create(createDTO(10L));

        verify(stringRedisTemplate).delete("dish:list:category:10");
    }

    @Test
    void updateWithoutCategoryChangeEvictsCurrentCategory() {
        when(dishMapper.selectById(1L)).thenReturn(dish(1L, 10L));
        when(categoryMapper.selectById(10L)).thenReturn(category(10L));
        when(dishMapper.selectByNameExcludeId("宫保鸡丁", 1L)).thenReturn(null);

        dishService.update(1L, updateDTO(10L));

        verify(stringRedisTemplate).delete("dish:list:category:10");
    }

    @Test
    void updateWithCategoryChangeEvictsOldAndNewCategories() {
        when(dishMapper.selectById(1L)).thenReturn(dish(1L, 10L));
        when(categoryMapper.selectById(11L)).thenReturn(category(11L));
        when(dishMapper.selectByNameExcludeId("宫保鸡丁", 1L)).thenReturn(null);

        dishService.update(1L, updateDTO(11L));

        verify(stringRedisTemplate).delete("dish:list:category:10");
        verify(stringRedisTemplate).delete("dish:list:category:11");
    }

    @Test
    void statusChangeEvictsCurrentCategory() {
        when(dishMapper.selectById(1L)).thenReturn(dish(1L, 10L));

        DishStatusDTO dto = new DishStatusDTO();
        dto.setStatus(0);
        dishService.updateStatus(1L, dto);

        verify(stringRedisTemplate).delete("dish:list:category:10");
    }

    @Test
    void redisDeleteFailureDoesNotFailCreateUpdateOrStatusChange() {
        doThrow(new IllegalStateException("redis down")).when(stringRedisTemplate).delete(any(String.class));
        when(categoryMapper.selectById(10L)).thenReturn(category(10L));
        when(dishMapper.selectByName("宫保鸡丁")).thenReturn(null);
        when(dishMapper.selectById(1L)).thenReturn(dish(1L, 10L), dish(1L, 10L));
        when(dishMapper.selectByNameExcludeId("宫保鸡丁", 1L)).thenReturn(null);

        assertThatCode(() -> dishService.create(createDTO(10L))).doesNotThrowAnyException();
        assertThatCode(() -> dishService.update(1L, updateDTO(10L))).doesNotThrowAnyException();

        DishStatusDTO statusDTO = new DishStatusDTO();
        statusDTO.setStatus(0);
        assertThatCode(() -> dishService.updateStatus(1L, statusDTO)).doesNotThrowAnyException();
    }

    @Test
    void updateUsesDatabaseLoadedOldCategoryForEviction() {
        when(dishMapper.selectById(1L)).thenReturn(dish(1L, 10L));
        when(categoryMapper.selectById(11L)).thenReturn(category(11L));
        when(dishMapper.selectByNameExcludeId("宫保鸡丁", 1L)).thenReturn(null);

        dishService.update(1L, updateDTO(11L));

        ArgumentCaptor<Dish> captor = ArgumentCaptor.forClass(Dish.class);
        verify(dishMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCategoryId()).isEqualTo(11L);
        verify(stringRedisTemplate).delete("dish:list:category:10");
        verify(stringRedisTemplate).delete("dish:list:category:11");
    }

    private DishListQueryDTO listQuery(Long categoryId) {
        DishListQueryDTO dto = new DishListQueryDTO();
        dto.setCategoryId(categoryId);
        return dto;
    }

    private DishCreateDTO createDTO(Long categoryId) {
        DishCreateDTO dto = new DishCreateDTO();
        dto.setCategoryId(categoryId);
        dto.setName("宫保鸡丁");
        dto.setPrice(new BigDecimal("18.00"));
        dto.setImage("dish.jpg");
        dto.setDescription("微辣");
        dto.setStatus(1);
        return dto;
    }

    private DishUpdateDTO updateDTO(Long categoryId) {
        DishUpdateDTO dto = new DishUpdateDTO();
        dto.setCategoryId(categoryId);
        dto.setName("宫保鸡丁");
        dto.setPrice(new BigDecimal("18.00"));
        dto.setImage("dish.jpg");
        dto.setDescription("微辣");
        dto.setStatus(1);
        return dto;
    }

    private Category category(Long id) {
        Category category = new Category();
        category.setId(id);
        category.setName("热菜");
        return category;
    }

    private Dish dish(Long id, Long categoryId) {
        Dish dish = new Dish();
        dish.setId(id);
        dish.setCategoryId(categoryId);
        dish.setName("宫保鸡丁");
        dish.setPrice(new BigDecimal("18.00"));
        dish.setImage("dish.jpg");
        dish.setDescription("微辣");
        dish.setStatus(1);
        return dish;
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
