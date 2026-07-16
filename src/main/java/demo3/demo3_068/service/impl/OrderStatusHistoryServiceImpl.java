package demo3.demo3_068.service.impl;

import demo3.demo3_068.common.Constants;
import demo3.demo3_068.entity.OrderStatusHistory;
import demo3.demo3_068.entity.Orders;
import demo3.demo3_068.mapper.OrderStatusHistoryMapper;
import demo3.demo3_068.mapper.UserMapper;
import demo3.demo3_068.model.OrderStatus;
import demo3.demo3_068.model.OrderStatusChangeOperation;
import demo3.demo3_068.model.Role;
import demo3.demo3_068.observability.TraceContext;
import demo3.demo3_068.service.OrderStatusHistoryService;
import demo3.demo3_068.vo.OrderStatusHistoryVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderStatusHistoryServiceImpl implements OrderStatusHistoryService {

    private final OrderStatusHistoryMapper orderStatusHistoryMapper;
    private final UserMapper userMapper;

    public OrderStatusHistoryServiceImpl(OrderStatusHistoryMapper orderStatusHistoryMapper,
                                         UserMapper userMapper) {
        this.orderStatusHistoryMapper = orderStatusHistoryMapper;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordChange(Orders orders,
                             Integer oldStatus,
                             OrderStatus newStatus,
                             OrderStatusChangeOperation operation,
                             Long operatorId,
                             Role operatorRole,
                             String reason) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(orders.getId());
        history.setOrderNumber(orders.getNumber());
        history.setUserId(orders.getUserId());
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus.getCode());
        history.setOperation(operation.name());
        history.setOperatorId(operatorId);
        history.setOperatorRole(operatorRole.name());
        history.setReason(normalizeReason(reason, operation));
        history.setTraceId(org.slf4j.MDC.get(TraceContext.TRACE_ID));
        history.setCreateTime(LocalDateTime.now());
        orderStatusHistoryMapper.insert(history);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSystemChange(Orders orders,
                                   Integer oldStatus,
                                   OrderStatus newStatus,
                                   OrderStatusChangeOperation operation,
                                   String reason) {
        Long systemOperatorId = userMapper.selectIdByUsername(Constants.SYSTEM_TIMEOUT_USERNAME);
        recordChange(orders, oldStatus, newStatus, operation, systemOperatorId, Role.SYSTEM, reason);
    }

    @Override
    public List<OrderStatusHistoryVO> listByOrderId(Long orderId) {
        return orderStatusHistoryMapper.selectByOrderId(orderId)
                .stream()
                .map(this::toVO)
                .toList();
    }

    private String normalizeReason(String reason, OrderStatusChangeOperation operation) {
        if (reason == null || reason.isBlank()) {
            return operation.getDefaultReason();
        }
        return reason.trim();
    }

    private OrderStatusHistoryVO toVO(OrderStatusHistory history) {
        OrderStatusChangeOperation operation = OrderStatusChangeOperation.parse(history.getOperation());
        return OrderStatusHistoryVO.builder()
                .id(history.getId())
                .orderId(history.getOrderId())
                .orderNumber(history.getOrderNumber())
                .userId(history.getUserId())
                .oldStatus(history.getOldStatus())
                .oldStatusLabel(statusLabel(history.getOldStatus()))
                .newStatus(history.getNewStatus())
                .newStatusLabel(statusLabel(history.getNewStatus()))
                .operation(history.getOperation())
                .operationText(operation.getDefaultReason())
                .operatorId(history.getOperatorId())
                .operatorRole(history.getOperatorRole())
                .reason(history.getReason())
                .traceId(history.getTraceId())
                .createTime(history.getCreateTime())
                .build();
    }

    private String statusLabel(Integer statusCode) {
        if (statusCode == null) {
            return null;
        }
        return OrderStatus.fromCode(statusCode).getLabel();
    }
}
