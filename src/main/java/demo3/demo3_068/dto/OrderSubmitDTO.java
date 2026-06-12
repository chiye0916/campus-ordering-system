package demo3.demo3_068.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrderSubmitDTO {

    @Size(max = 255, message = "备注不能超过255位")
    private String remark;
}
