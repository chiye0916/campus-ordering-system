package demo3.demo3_068.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MockPaymentCallbackDTO {

    @NotBlank
    private String tradeNo;

    @NotBlank
    private String callbackNo;

    private String thirdTradeNo;

    @NotBlank
    private String payStatus;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal amount;

    @NotNull
    private LocalDateTime callbackTime;
}
