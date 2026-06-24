package demo3.demo3_068.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentRecord {

    private Long id;
    private Long orderId;
    private String orderNumber;
    private Long userId;
    private BigDecimal amount;
    private String payChannel;
    private String tradeNo;
    private String thirdTradeNo;
    private Integer status;
    private LocalDateTime requestTime;
    private LocalDateTime successTime;
    private LocalDateTime callbackTime;
    private String failureReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
