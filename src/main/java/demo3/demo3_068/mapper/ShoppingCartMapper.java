package demo3.demo3_068.mapper;

import demo3.demo3_068.entity.ShoppingCart;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ShoppingCartMapper {

    ShoppingCart selectByUserIdAndDishId(@Param("userId") Long userId, @Param("dishId") Long dishId);

    List<ShoppingCart> selectByUserId(@Param("userId") Long userId);

    int insert(ShoppingCart shoppingCart);

    int updateQuantityById(@Param("id") Long id, @Param("quantity") Integer quantity);

    int updateItemById(@Param("id") Long id,
                       @Param("dishName") String dishName,
                       @Param("dishPrice") BigDecimal dishPrice,
                       @Param("quantity") Integer quantity);

    int deleteByUserIdAndDishId(@Param("userId") Long userId, @Param("dishId") Long dishId);

    int deleteByUserId(@Param("userId") Long userId);
}
