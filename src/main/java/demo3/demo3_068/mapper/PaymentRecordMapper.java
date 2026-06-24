package demo3.demo3_068.mapper;

import demo3.demo3_068.entity.PaymentRecord;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface PaymentRecordMapper {

    int insert(PaymentRecord paymentRecord);

    int updateStatusToSuccessById(@Param("id") Long id,
                                  @Param("successTime") LocalDateTime successTime,
                                  @Param("oldStatus") Integer oldStatus,
                                  @Param("newStatus") Integer newStatus);
}
