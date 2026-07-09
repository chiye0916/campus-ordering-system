package demo3.demo3_068.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderTimeoutMessageDTO {

    private Long orderId;
    private String messageId;
    private LocalDateTime expireTime;
}
