package demo3.demo3_068.mapper;

import demo3.demo3_068.entity.Category;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CategoryMapper {

    Category selectById(@Param("id") Long id);

    Category selectByName(@Param("name") String name);

    Category selectByNameExcludeId(@Param("name") String name, @Param("id") Long id);

    List<Category> selectAllOrderBySort();

    int insert(Category category);

    int updateById(Category category);

    int deleteById(@Param("id") Long id);
}
