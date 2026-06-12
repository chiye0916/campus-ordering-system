package demo3.demo3_068.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderDetail {

    private Long id;
    private Long orderId;
    private Long dishId;
    private String dishName;
    private BigDecimal dishPrice;
    private Integer quantity;
    private BigDecimal amount;
}
