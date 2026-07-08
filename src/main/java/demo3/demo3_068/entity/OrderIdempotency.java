package demo3.demo3_068.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrderIdempotency {

    private Long id;
    private Long userId;
    private String idempotencyKey;
    private String requestHash;
    private Long orderId;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
