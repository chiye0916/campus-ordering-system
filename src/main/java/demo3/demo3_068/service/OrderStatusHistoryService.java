package demo3.demo3_068.service;

import demo3.demo3_068.entity.Orders;
import demo3.demo3_068.model.OrderStatus;
import demo3.demo3_068.model.OrderStatusChangeOperation;
import demo3.demo3_068.model.Role;
import demo3.demo3_068.vo.OrderStatusHistoryVO;

import java.util.List;

public interface OrderStatusHistoryService {

    void recordChange(Orders orders,
                      Integer oldStatus,
                      OrderStatus newStatus,
                      OrderStatusChangeOperation operation,
                      Long operatorId,
                      Role operatorRole,
                      String reason);

    void recordSystemChange(Orders orders,
                            Integer oldStatus,
                            OrderStatus newStatus,
                            OrderStatusChangeOperation operation,
                            String reason);

    List<OrderStatusHistoryVO> listByOrderId(Long orderId);
}
