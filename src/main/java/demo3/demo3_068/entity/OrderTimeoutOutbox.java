package demo3.demo3_068.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrderTimeoutOutbox {

    private Long id;
    private Long orderId;
    private String messageId;
    private String traceId;
    private String payload;
    private LocalDateTime expireTime;
    private Integer status;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private LocalDateTime publishClaimTime;
    private LocalDateTime sentTime;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
