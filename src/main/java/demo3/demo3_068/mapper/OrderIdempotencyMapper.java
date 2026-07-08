package demo3.demo3_068.mapper;

import demo3.demo3_068.entity.OrderIdempotency;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface OrderIdempotencyMapper {

    int insert(OrderIdempotency orderIdempotency);

    OrderIdempotency selectByUserIdAndKey(@Param("userId") Long userId,
                                          @Param("idempotencyKey") String idempotencyKey);

    int markSucceeded(@Param("id") Long id,
                      @Param("orderId") Long orderId,
                      @Param("status") Integer status,
                      @Param("updateTime") LocalDateTime updateTime);

    int markFailed(@Param("id") Long id,
                   @Param("status") Integer status,
                   @Param("updateTime") LocalDateTime updateTime);
}
