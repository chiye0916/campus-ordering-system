package demo3.demo3_068.mapper;

import demo3.demo3_068.entity.OrderStatusHistory;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OrderStatusHistoryMapper {

    int insert(OrderStatusHistory history);

    List<OrderStatusHistory> selectByOrderId(@Param("orderId") Long orderId);
}
