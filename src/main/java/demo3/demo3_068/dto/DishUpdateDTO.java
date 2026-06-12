package demo3.demo3_068.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DishUpdateDTO {

    @NotNull(message = "分类ID不能为空")
    private Long categoryId;

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 64, message = "商品名称不能超过64位")
    private String name;

    @NotNull(message = "商品价格不能为空")
    @DecimalMin(value = "0.01", message = "商品价格必须大于0")
    private BigDecimal price;

    @Size(max = 255, message = "图片地址不能超过255位")
    private String image;

    @Size(max = 255, message = "商品描述不能超过255位")
    private String description;

    @NotNull(message = "商品状态不能为空")
    @Min(value = 0, message = "商品状态只能是0或1")
    @Max(value = 1, message = "商品状态只能是0或1")
    private Integer status;
}
