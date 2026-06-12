package demo3.demo3_068.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class OrderPayVO {

    private Long orderId;
    private String orderNumber;
    private Integer status;
    private BigDecimal amount;
    private LocalDateTime payTime;
}
