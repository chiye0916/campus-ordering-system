package demo3.demo3_068.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import demo3.demo3_068.common.PageResult;
import demo3.demo3_068.dto.DishCreateDTO;
import demo3.demo3_068.dto.DishListQueryDTO;
import demo3.demo3_068.dto.DishPageQueryDTO;
import demo3.demo3_068.dto.DishStatusDTO;
import demo3.demo3_068.dto.DishUpdateDTO;
import demo3.demo3_068.entity.Category;
import demo3.demo3_068.entity.Dish;
import demo3.demo3_068.exception.BusinessException;
import demo3.demo3_068.mapper.CategoryMapper;
import demo3.demo3_068.mapper.DishMapper;
import demo3.demo3_068.service.DishService;
import demo3.demo3_068.vo.DishVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DishServiceImpl implements DishService {

    private static final String DISH_LIST_CACHE_KEY_PREFIX = "dish:list:category:";
    private static final TypeReference<List<DishVO>> DISH_VO_LIST_TYPE = new TypeReference<>() {
    };

    private final DishMapper dishMapper;
    private final CategoryMapper categoryMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public DishServiceImpl(DishMapper dishMapper,
                           CategoryMapper categoryMapper,
                           StringRedisTemplate stringRedisTemplate,
                           ObjectMapper objectMapper) {
        this.dishMapper = dishMapper;
        this.categoryMapper = categoryMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Long create(DishCreateDTO dishCreateDTO) {
        Category category = getCategoryOrThrow(dishCreateDTO.getCategoryId());
        Dish duplicateDish = dishMapper.selectByName(dishCreateDTO.getName());
        if (duplicateDish != null) {
            throw new BusinessException("商品名称已存在");
        }

        Dish dish = new Dish();
        dish.setCategoryId(category.getId());
        dish.setName(dishCreateDTO.getName());
        dish.setPrice(dishCreateDTO.getPrice());
        dish.setImage(dishCreateDTO.getImage());
        dish.setDescription(dishCreateDTO.getDescription());
        dish.setStatus(dishCreateDTO.getStatus());
        dishMapper.insert(dish);
        deleteDishListCache(category.getId());
        return dish.getId();
    }

    @Override
    public PageResult<DishVO> page(DishPageQueryDTO dishPageQueryDTO) {
        int offset = (dishPageQueryDTO.getPage() - 1) * dishPageQueryDTO.getPageSize();
        long total = dishMapper.countPage(dishPageQueryDTO);
        List<Dish> dishes = dishMapper.selectPage(dishPageQueryDTO, offset, dishPageQueryDTO.getPageSize());
        Map<Long, Category> categoryMap = loadCategoryMap(dishes);
        List<DishVO> records = dishes.stream()
                .map(dish -> toDishVO(dish, categoryMap.get(dish.getCategoryId())))
                .toList();
        return new PageResult<>(total, records);
    }

    @Override
    public List<DishVO> list(DishListQueryDTO dishListQueryDTO) {
        Category category = getCategoryOrThrow(dishListQueryDTO.getCategoryId());
        String cacheKey = buildDishListCacheKey(category.getId());
        String cachedValue = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cachedValue != null) {
            try {
                return objectMapper.readValue(cachedValue, DISH_VO_LIST_TYPE);
            } catch (Exception e) {
                stringRedisTemplate.delete(cacheKey);
            }
        }

        List<DishVO> records = dishMapper.selectAvailableByCategoryId(category.getId()).stream()
                .map(dish -> toDishVO(dish, category))
                .toList();
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(records));
        } catch (Exception e) {
            throw new BusinessException(500, "写入商品缓存失败");
        }
        return records;
    }

    @Override
    public void update(Long id, DishUpdateDTO dishUpdateDTO) {
        Dish dish = getDishOrThrow(id);
        Category category = getCategoryOrThrow(dishUpdateDTO.getCategoryId());
        Dish duplicateDish = dishMapper.selectByNameExcludeId(dishUpdateDTO.getName(), id);
        if (duplicateDish != null) {
            throw new BusinessException("商品名称已存在");
        }

        Long oldCategoryId = dish.getCategoryId();
        dish.setCategoryId(category.getId());
        dish.setName(dishUpdateDTO.getName());
        dish.setPrice(dishUpdateDTO.getPrice());
        dish.setImage(dishUpdateDTO.getImage());
        dish.setDescription(dishUpdateDTO.getDescription());
        dish.setStatus(dishUpdateDTO.getStatus());
        dishMapper.updateById(dish);

        deleteDishListCache(oldCategoryId);
        if (!Objects.equals(oldCategoryId, category.getId())) {
            deleteDishListCache(category.getId());
        }
    }

    @Override
    public void updateStatus(Long id, DishStatusDTO dishStatusDTO) {
        Dish dish = getDishOrThrow(id);
        dishMapper.updateStatusById(id, dishStatusDTO.getStatus());
        deleteDishListCache(dish.getCategoryId());
    }

    private Dish getDishOrThrow(Long id) {
        Dish dish = dishMapper.selectById(id);
        if (dish == null) {
            throw new BusinessException(404, "商品不存在");
        }
        return dish;
    }

    private Category getCategoryOrThrow(Long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException(404, "分类不存在");
        }
        return category;
    }

    private Map<Long, Category> loadCategoryMap(List<Dish> dishes) {
        return dishes.stream()
                .map(Dish::getCategoryId)
                .distinct()
                .map(categoryMapper::selectById)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Category::getId, Function.identity()));
    }

    private DishVO toDishVO(Dish dish, Category category) {
        return DishVO.builder()
                .id(dish.getId())
                .categoryId(dish.getCategoryId())
                .categoryName(category == null ? null : category.getName())
                .name(dish.getName())
                .price(dish.getPrice())
                .image(dish.getImage())
                .description(dish.getDescription())
                .status(dish.getStatus())
                .build();
    }

    private void deleteDishListCache(Long categoryId) {
        stringRedisTemplate.delete(buildDishListCacheKey(categoryId));
    }

    private String buildDishListCacheKey(Long categoryId) {
        return DISH_LIST_CACHE_KEY_PREFIX + categoryId;
    }
}
