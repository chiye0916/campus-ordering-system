package demo3.demo3_068.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StockRecord {

    private Long id;
    private Long dishId;
    private Long orderId;
    private String changeType;
    private Integer changeQuantity;
    private Integer availableBefore;
    private Integer availableAfter;
    private Integer lockedBefore;
    private Integer lockedAfter;
    private Long operatorId;
    private String remark;
    private LocalDateTime createTime;
}
