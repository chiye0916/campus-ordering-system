package demo3.demo3_068.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String role;
}
