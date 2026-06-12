package demo3.demo3_068.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Orders {

    private Long id;
    private String number;
    private Long userId;
    private Integer status;
    private BigDecimal amount;
    private String remark;
    private LocalDateTime orderTime;
    private LocalDateTime payTime;
    private LocalDateTime cancelTime;
    private LocalDateTime completeTime;
}
