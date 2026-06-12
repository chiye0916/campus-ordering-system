package demo3.demo3_068.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CategoryVO {

    private Long id;
    private String name;
    private Integer sort;
}
