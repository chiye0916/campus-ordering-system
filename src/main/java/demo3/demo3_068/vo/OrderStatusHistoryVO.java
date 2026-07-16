package demo3.demo3_068.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class OrderStatusHistoryVO {

    private Long id;
    private Long orderId;
    private String orderNumber;
    private Long userId;
    private Integer oldStatus;
    private String oldStatusLabel;
    private Integer newStatus;
    private String newStatusLabel;
    private String operation;
    private String operationText;
    private Long operatorId;
    private String operatorRole;
    private String reason;
    private String traceId;
    private LocalDateTime createTime;
}
