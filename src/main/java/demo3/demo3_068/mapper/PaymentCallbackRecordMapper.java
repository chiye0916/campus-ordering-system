package demo3.demo3_068.mapper;

import demo3.demo3_068.entity.PaymentCallbackRecord;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface PaymentCallbackRecordMapper {

    int insert(PaymentCallbackRecord paymentCallbackRecord);

    PaymentCallbackRecord selectByCallbackNo(@Param("callbackNo") String callbackNo);

    int updateProcessingForRetry(@Param("id") Long id,
                                 @Param("rawPayload") String rawPayload,
                                 @Param("updateTime") LocalDateTime updateTime);

    int finalizeById(@Param("id") Long id,
                     @Param("paymentRecordId") Long paymentRecordId,
                     @Param("orderId") Long orderId,
                     @Param("processStatus") Integer processStatus,
                     @Param("failureReason") String failureReason,
                     @Param("updateTime") LocalDateTime updateTime);
}
