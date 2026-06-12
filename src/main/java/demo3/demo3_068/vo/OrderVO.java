package demo3.demo3_068.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class OrderVO {

    private Long id;
    private String number;
    private Integer status;
    private BigDecimal amount;
    private LocalDateTime orderTime;
}
