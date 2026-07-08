package demo3.demo3_068.mapper;

import demo3.demo3_068.entity.StockRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface StockRecordMapper {

    int insert(StockRecord stockRecord);

    List<StockRecord> selectByDishId(@Param("dishId") Long dishId);

    List<StockRecord> selectByOrderId(@Param("orderId") Long orderId);
}
