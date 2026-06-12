package demo3.demo3_068.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ShoppingCart {

    private Long id;
    private Long userId;
    private Long dishId;
    private String dishName;
    private BigDecimal dishPrice;
    private Integer quantity;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
