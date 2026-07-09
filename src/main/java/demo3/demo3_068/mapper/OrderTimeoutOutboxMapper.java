package demo3.demo3_068.mapper;

import demo3.demo3_068.entity.OrderTimeoutOutbox;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderTimeoutOutboxMapper {

    int insert(OrderTimeoutOutbox orderTimeoutOutbox);

    OrderTimeoutOutbox selectByOrderId(@Param("orderId") Long orderId);

    List<OrderTimeoutOutbox> selectDueRows(@Param("now") LocalDateTime now,
                                           @Param("pendingStatus") Integer pendingStatus,
                                           @Param("failedStatus") Integer failedStatus,
                                           @Param("maxRetryCount") Integer maxRetryCount,
                                           @Param("limit") Integer limit);

    int claimForPublishing(@Param("id") Long id,
                           @Param("now") LocalDateTime now,
                           @Param("pendingStatus") Integer pendingStatus,
                           @Param("failedStatus") Integer failedStatus,
                           @Param("publishingStatus") Integer publishingStatus,
                           @Param("maxRetryCount") Integer maxRetryCount);

    int markSent(@Param("id") Long id,
                 @Param("now") LocalDateTime now,
                 @Param("publishingStatus") Integer publishingStatus,
                 @Param("sentStatus") Integer sentStatus);

    int markPublishFailed(@Param("id") Long id,
                          @Param("lastError") String lastError,
                          @Param("now") LocalDateTime now,
                          @Param("nextRetryTime") LocalDateTime nextRetryTime,
                          @Param("publishingStatus") Integer publishingStatus,
                          @Param("failedStatus") Integer failedStatus);

    int recoverStalePublishing(@Param("now") LocalDateTime now,
                               @Param("claimDeadline") LocalDateTime claimDeadline,
                               @Param("nextRetryTime") LocalDateTime nextRetryTime,
                               @Param("publishingStatus") Integer publishingStatus,
                               @Param("failedStatus") Integer failedStatus,
                               @Param("lastError") String lastError,
                               @Param("maxRetryCount") Integer maxRetryCount);
}
