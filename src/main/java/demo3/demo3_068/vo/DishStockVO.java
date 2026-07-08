package demo3.demo3_068.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DishStockVO {

    private Long dishId;
    private Integer availableStock;
    private Integer lockedStock;
    private Integer version;
}
