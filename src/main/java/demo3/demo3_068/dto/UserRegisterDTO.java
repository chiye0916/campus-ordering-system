package demo3.demo3_068.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64, message = "用户名长度必须在3到64位之间")
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式错误")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度必须在6到32位之间")
    private String password;

    @NotBlank(message = "邮箱验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "邮箱验证码必须是6位数字")
    private String emailCode;

    @Size(max = 64, message = "昵称不能超过64位")
    private String nickname;

    private String role;
}
