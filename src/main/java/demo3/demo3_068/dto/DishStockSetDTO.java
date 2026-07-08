package demo3.demo3_068.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DishStockSetDTO {

    @NotNull(message = "可用库存不能为空")
    @Min(value = 0, message = "可用库存不能小于0")
    private Integer availableStock;

    @Size(max = 255, message = "备注不能超过255位")
    private String remark;
}
