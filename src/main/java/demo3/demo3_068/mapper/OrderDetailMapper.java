package demo3.demo3_068.mapper;

import demo3.demo3_068.entity.OrderDetail;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface OrderDetailMapper {

    List<OrderDetail> selectByOrderId(@Param("orderId") Long orderId);

    int insertBatch(@Param("details") List<OrderDetail> details);
}
