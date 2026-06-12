package demo3.demo3_068.mapper;

import demo3.demo3_068.dto.DishPageQueryDTO;
import demo3.demo3_068.entity.Dish;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DishMapper {

    int countByCategoryId(@Param("categoryId") Long categoryId);

    Dish selectById(@Param("id") Long id);

    Dish selectByName(@Param("name") String name);

    Dish selectByNameExcludeId(@Param("name") String name, @Param("id") Long id);

    List<Dish> selectPage(@Param("query") DishPageQueryDTO query,
                          @Param("offset") int offset,
                          @Param("pageSize") int pageSize);

    long countPage(@Param("query") DishPageQueryDTO query);

    List<Dish> selectAvailableByCategoryId(@Param("categoryId") Long categoryId);

    int insert(Dish dish);

    int updateById(Dish dish);

    int updateStatusById(@Param("id") Long id, @Param("status") Integer status);
}
