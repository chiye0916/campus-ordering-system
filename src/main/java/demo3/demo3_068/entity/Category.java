package demo3.demo3_068.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Category {

    private Long id;
    private String name;
    private Integer sort;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
