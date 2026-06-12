package demo3.demo3_068.service.impl;

import demo3.demo3_068.dto.CategoryCreateDTO;
import demo3.demo3_068.dto.CategoryUpdateDTO;
import demo3.demo3_068.entity.Category;
import demo3.demo3_068.exception.BusinessException;
import demo3.demo3_068.mapper.CategoryMapper;
import demo3.demo3_068.mapper.DishMapper;
import demo3.demo3_068.service.CategoryService;
import demo3.demo3_068.vo.CategoryVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final DishMapper dishMapper;

    public CategoryServiceImpl(CategoryMapper categoryMapper, DishMapper dishMapper) {
        this.categoryMapper = categoryMapper;
        this.dishMapper = dishMapper;
    }

    @Override
    public Long create(CategoryCreateDTO categoryCreateDTO) {
        Category existingCategory = categoryMapper.selectByName(categoryCreateDTO.getName());
        if (existingCategory != null) {
            throw new BusinessException("分类名称已存在");
        }

        Category category = new Category();
        category.setName(categoryCreateDTO.getName());
        category.setSort(categoryCreateDTO.getSort());
        categoryMapper.insert(category);
        return category.getId();
    }

    @Override
    public List<CategoryVO> list() {
        return categoryMapper.selectAllOrderBySort().stream()
                .map(this::toCategoryVO)
                .toList();
    }

    @Override
    public void update(Long id, CategoryUpdateDTO categoryUpdateDTO) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(404, "分类不存在");
        }

        Category duplicateCategory = categoryMapper.selectByNameExcludeId(categoryUpdateDTO.getName(), id);
        if (duplicateCategory != null) {
            throw new BusinessException("分类名称已存在");
        }

        category.setName(categoryUpdateDTO.getName());
        category.setSort(categoryUpdateDTO.getSort());
        categoryMapper.updateById(category);
    }

    @Override
    public void delete(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(404, "分类不存在");
        }

        int dishCount = dishMapper.countByCategoryId(id);
        if (dishCount > 0) {
            throw new BusinessException("该分类下存在商品，不能删除");
        }

        categoryMapper.deleteById(id);
    }

    private CategoryVO toCategoryVO(Category category) {
        return CategoryVO.builder()
                .id(category.getId())
                .name(category.getName())
                .sort(category.getSort())
                .build();
    }
}
