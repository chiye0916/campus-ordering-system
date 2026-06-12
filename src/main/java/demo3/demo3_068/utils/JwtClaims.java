package demo3.demo3_068.utils;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JwtClaims {

    private Long userId;
    private String username;
    private String role;
}
