package demo3.demo3_068.mapper;

import demo3.demo3_068.dto.OrderPageQueryDTO;
import demo3.demo3_068.entity.Orders;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OrdersMapper {

    Orders selectById(@Param("id") Long id);

    List<Orders> selectPage(@Param("userId") Long userId,
                            @Param("query") OrderPageQueryDTO query,
                            @Param("offset") int offset,
                            @Param("pageSize") int pageSize);

    long countPage(@Param("userId") Long userId, @Param("query") OrderPageQueryDTO query);

    int insert(Orders orders);

    int updateStatusById(Orders orders);
}
