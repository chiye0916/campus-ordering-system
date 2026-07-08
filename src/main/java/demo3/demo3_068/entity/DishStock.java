package demo3.demo3_068.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DishStock {

    private Long id;
    private Long dishId;
    private Integer availableStock;
    private Integer lockedStock;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
