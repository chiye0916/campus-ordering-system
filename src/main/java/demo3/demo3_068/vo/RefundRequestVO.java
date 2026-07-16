package demo3.demo3_068.vo;

import demo3.demo3_068.model.RefundRequestStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class RefundRequestVO {

    private Long id;
    private String refundNo;
    private Long orderId;
    private Long userId;
    private String orderNumber;
    private BigDecimal amount;
    private String reason;
    private RefundRequestStatus status;
    private String rejectReason;
    private Long reviewerId;
    private LocalDateTime reviewTime;
    private LocalDateTime completeTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
