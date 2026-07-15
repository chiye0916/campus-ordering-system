package demo3.demo3_068.mapper;

import demo3.demo3_068.dto.OrderPageQueryDTO;
import demo3.demo3_068.entity.Orders;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrdersMapper {

    Orders selectById(@Param("id") Long id);

    List<Orders> selectPage(@Param("userId") Long userId,
                            @Param("visibleStatusCodes") List<Integer> visibleStatusCodes,
                            @Param("query") OrderPageQueryDTO query,
                            @Param("offset") int offset,
                            @Param("pageSize") int pageSize);

    long countPage(@Param("userId") Long userId,
                   @Param("visibleStatusCodes") List<Integer> visibleStatusCodes,
                   @Param("query") OrderPageQueryDTO query);

    int insert(Orders orders);

    int updateToPaidById(@Param("id") Long id,
                         @Param("payTime") LocalDateTime payTime,
                         @Param("oldStatus") Integer oldStatus,
                         @Param("newStatus") Integer newStatus);

    int updateToCancelledById(@Param("id") Long id,
                              @Param("cancelTime") LocalDateTime cancelTime,
                              @Param("oldStatus") Integer oldStatus,
                              @Param("newStatus") Integer newStatus);

    int updateToCompletedById(@Param("id") Long id,
                              @Param("completeTime") LocalDateTime completeTime,
                              @Param("oldStatus") Integer oldStatus,
                              @Param("newStatus") Integer newStatus);

    int updateStatusById(@Param("id") Long id,
                         @Param("oldStatus") Integer oldStatus,
                         @Param("newStatus") Integer newStatus);
}
