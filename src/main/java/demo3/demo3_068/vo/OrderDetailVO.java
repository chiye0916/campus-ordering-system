package demo3.demo3_068.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderDetailVO {

    private Long id;
    private String number;
    private Integer status;
    private BigDecimal amount;
    private String remark;
    private LocalDateTime orderTime;
    private LocalDateTime payTime;
    private LocalDateTime cancelTime;
    private LocalDateTime completeTime;
    private List<OrderItemVO> items;
}
