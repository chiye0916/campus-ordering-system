package demo3.demo3_068.mapper;

import demo3.demo3_068.entity.PaymentRecord;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface PaymentRecordMapper {

    int insert(PaymentRecord paymentRecord);

    PaymentRecord selectByTradeNo(@Param("tradeNo") String tradeNo);

    PaymentRecord selectLatestMockByOrderId(@Param("orderId") Long orderId,
                                            @Param("payChannel") String payChannel);

    int updateStatusToSuccessById(@Param("id") Long id,
                                  @Param("successTime") LocalDateTime successTime,
                                  @Param("callbackTime") LocalDateTime callbackTime,
                                  @Param("thirdTradeNo") String thirdTradeNo,
                                  @Param("oldStatus") Integer oldStatus,
                                  @Param("newStatus") Integer newStatus);

    int updateStatusToFailedById(@Param("id") Long id,
                                 @Param("callbackTime") LocalDateTime callbackTime,
                                 @Param("thirdTradeNo") String thirdTradeNo,
                                 @Param("failureReason") String failureReason,
                                 @Param("oldStatus") Integer oldStatus,
                                 @Param("newStatus") Integer newStatus);

    int closeCurrentPayingMockByOrderId(@Param("orderId") Long orderId,
                                        @Param("payChannel") String payChannel,
                                        @Param("oldStatus") Integer oldStatus,
                                        @Param("newStatus") Integer newStatus,
                                        @Param("updateTime") LocalDateTime updateTime);
}
