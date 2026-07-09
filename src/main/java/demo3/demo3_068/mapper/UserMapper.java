package demo3.demo3_068.mapper;

import demo3.demo3_068.entity.User;
import org.apache.ibatis.annotations.Param;

public interface UserMapper {

    User selectByUsername(@Param("username") String username);

    User selectByEmail(@Param("email") String email);

    User selectById(@Param("id") Long id);

    Long selectIdByUsername(@Param("username") String username);

    int insert(User user);
}
