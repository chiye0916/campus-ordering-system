package demo3.demo3_068.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DishListQueryDTO {

    @NotNull(message = "分类ID不能为空")
    private Long categoryId;
}
