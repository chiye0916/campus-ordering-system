package demo3.demo3_068.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentCallbackRecord {

    private Long id;
    private Long paymentRecordId;
    private Long orderId;
    private String tradeNo;
    private String callbackNo;
    private String thirdTradeNo;
    private String payStatus;
    private BigDecimal amount;
    private LocalDateTime callbackTime;
    private Integer processStatus;
    private String failureReason;
    private String rawPayload;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
