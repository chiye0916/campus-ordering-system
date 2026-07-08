package demo3.demo3_068.mapper;

import demo3.demo3_068.entity.DishStock;
import org.apache.ibatis.annotations.Param;

public interface DishStockMapper {

    DishStock selectByDishId(@Param("dishId") Long dishId);

    DishStock selectByDishIdForUpdate(@Param("dishId") Long dishId);

    int insert(DishStock dishStock);

    int updateAvailableStockByDishId(@Param("dishId") Long dishId,
                                     @Param("availableStock") Integer availableStock);

    int lockStock(@Param("dishId") Long dishId, @Param("quantity") Integer quantity);

    int confirmLockedStock(@Param("dishId") Long dishId, @Param("quantity") Integer quantity);

    int releaseLockedStock(@Param("dishId") Long dishId, @Param("quantity") Integer quantity);
}
