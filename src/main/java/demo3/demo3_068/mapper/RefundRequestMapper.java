package demo3.demo3_068.mapper;

import demo3.demo3_068.entity.RefundRequest;
import demo3.demo3_068.model.RefundRequestStatus;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RefundRequestMapper {

    int insert(RefundRequest refundRequest);

    RefundRequest selectById(@Param("id") Long id);

    RefundRequest selectByOrderId(@Param("orderId") Long orderId);

    long countPage(@Param("userId") Long userId,
                   @Param("status") RefundRequestStatus status);

    List<RefundRequest> selectPage(@Param("userId") Long userId,
                                   @Param("status") RefundRequestStatus status,
                                   @Param("offset") int offset,
                                   @Param("pageSize") int pageSize);

    int approvePending(@Param("id") Long id,
                       @Param("oldStatus") RefundRequestStatus oldStatus,
                       @Param("newStatus") RefundRequestStatus newStatus,
                       @Param("reviewerId") Long reviewerId,
                       @Param("reviewTime") LocalDateTime reviewTime,
                       @Param("updateTime") LocalDateTime updateTime);

    int rejectPending(@Param("id") Long id,
                      @Param("oldStatus") RefundRequestStatus oldStatus,
                      @Param("newStatus") RefundRequestStatus newStatus,
                      @Param("reviewerId") Long reviewerId,
                      @Param("reviewTime") LocalDateTime reviewTime,
                      @Param("rejectReason") String rejectReason,
                      @Param("updateTime") LocalDateTime updateTime);

    int completeApproved(@Param("id") Long id,
                         @Param("oldStatus") RefundRequestStatus oldStatus,
                         @Param("newStatus") RefundRequestStatus newStatus,
                         @Param("completeTime") LocalDateTime completeTime,
                         @Param("updateTime") LocalDateTime updateTime);
}
