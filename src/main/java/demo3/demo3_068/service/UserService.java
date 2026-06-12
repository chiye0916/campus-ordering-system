package demo3.demo3_068.service;

import demo3.demo3_068.dto.UserLoginDTO;
import demo3.demo3_068.dto.UserRegisterDTO;
import demo3.demo3_068.vo.UserLoginVO;
import demo3.demo3_068.vo.UserVO;

public interface UserService {

    Long register(UserRegisterDTO userRegisterDTO);

    UserLoginVO login(UserLoginDTO userLoginDTO);

    void logout();

    UserVO getCurrentUser();
}
