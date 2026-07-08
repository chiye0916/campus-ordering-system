package demo3.demo3_068.service;

import demo3.demo3_068.dto.DishStockSetDTO;
import demo3.demo3_068.vo.DishStockVO;

import java.util.Map;

public interface DishStockService {

    DishStockVO getStock(Long dishId);

    void setStock(Long dishId, DishStockSetDTO dishStockSetDTO);

    void lockStock(Long orderId, Map<Long, Integer> dishQuantities, Long operatorId);

    void confirmLockedStock(Long orderId, Map<Long, Integer> dishQuantities, Long operatorId);

    void releaseLockedStock(Long orderId, Map<Long, Integer> dishQuantities, Long operatorId);
}
