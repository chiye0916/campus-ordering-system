package demo3.demo3_068.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrderStatusHistory {

    private Long id;
    private Long orderId;
    private String orderNumber;
    private Long userId;
    private Integer oldStatus;
    private Integer newStatus;
    private String operation;
    private Long operatorId;
    private String operatorRole;
    private String reason;
    private String traceId;
    private LocalDateTime createTime;
}
