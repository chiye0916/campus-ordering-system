package demo3.demo3_068.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class User {

    private Long id;
    private String username;
    private String email;
    private String password;
    private String nickname;
    private String role;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
