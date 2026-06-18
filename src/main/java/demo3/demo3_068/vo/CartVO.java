package demo3.demo3_068.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CartVO {

    private Long dishId;
    private String dishName;
    private BigDecimal dishPrice;
    private Integer quantity;
    private BigDecimal amount;
    private Integer dishStatus;
    private Boolean available;
    private String changeMessage;
}
