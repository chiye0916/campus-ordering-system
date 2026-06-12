package demo3.demo3_068.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserLoginVO {

    private Long id;
    private String username;
    private String nickname;
    private String role;
    private String token;
}
