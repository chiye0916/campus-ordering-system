package demo3.demo3_068.controller;

import demo3.demo3_068.common.Result;
import demo3.demo3_068.dto.UserLoginDTO;
import demo3.demo3_068.dto.UserRegisterDTO;
import demo3.demo3_068.service.UserService;
import demo3.demo3_068.vo.UserLoginVO;
import demo3.demo3_068.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody UserRegisterDTO userRegisterDTO) {
        return Result.success(userService.register(userRegisterDTO));
    }

    @PostMapping("/login")
    public Result<UserLoginVO> login(@Valid @RequestBody UserLoginDTO userLoginDTO) {
        return Result.success(userService.login(userLoginDTO));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        userService.logout();
        return Result.success();
    }

    @GetMapping("/me")
    public Result<UserVO> getCurrentUser() {
        return Result.success(userService.getCurrentUser());
    }
}
