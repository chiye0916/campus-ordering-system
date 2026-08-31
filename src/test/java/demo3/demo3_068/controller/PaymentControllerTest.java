package demo3.demo3_068.controller;

import demo3.demo3_068.common.Result;
import demo3.demo3_068.dto.MockPaymentCallbackDTO;
import demo3.demo3_068.exception.PaymentCallbackRetryableException;
import demo3.demo3_068.model.MockPayStatus;
import demo3.demo3_068.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PaymentControllerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(PaymentService.class, () -> mock(PaymentService.class))
            .withUserConfiguration(PaymentController.class);

    @Test
    void mockControllerIsDisabledUnlessExplicitlyEnabled() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(PaymentController.class));
        contextRunner.withPropertyValues("payment.mock.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(PaymentController.class));
    }

    @Test
    void mockControllerIsAvailableWhenExplicitlyEnabled() {
        contextRunner.withPropertyValues("payment.mock.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(PaymentController.class));
    }

    @Test
    void callbackRejectsUnsupportedPayStatusBeforeDelegating() {
        PaymentService paymentService = mock(PaymentService.class);
        PaymentController controller = new PaymentController(paymentService);
        MockPaymentCallbackDTO dto = callback("UNKNOWN");

        Result<Void> result = controller.mockCallback(dto);

        assertThat(result.getCode()).isEqualTo(400);
        verify(paymentService, never()).handleMockCallback(dto);
    }

    @Test
    void callbackDelegatesSupportedRequest() {
        PaymentService paymentService = mock(PaymentService.class);
        PaymentController controller = new PaymentController(paymentService);
        MockPaymentCallbackDTO dto = callback(MockPayStatus.SUCCESS);

        Result<Void> result = controller.mockCallback(dto);

        assertThat(result.getCode()).isEqualTo(200);
        verify(paymentService).handleMockCallback(dto);
    }

    @Test
    void retryableCallbackConflictReturnsNonSuccess() {
        PaymentService paymentService = mock(PaymentService.class);
        PaymentController controller = new PaymentController(paymentService);
        MockPaymentCallbackDTO dto = callback(MockPayStatus.SUCCESS);
        doThrow(new PaymentCallbackRetryableException("支付回调正在处理中，请稍后重试"))
                .when(paymentService).handleMockCallback(dto);

        Result<Void> result = controller.mockCallback(dto);

        assertThat(result.getCode()).isEqualTo(409);
    }

    private MockPaymentCallbackDTO callback(String status) {
        MockPaymentCallbackDTO dto = new MockPaymentCallbackDTO();
        dto.setTradeNo("PAY001");
        dto.setCallbackNo("CB001");
        dto.setPayStatus(status);
        dto.setAmount(new BigDecimal("30.00"));
        dto.setCallbackTime(LocalDateTime.of(2026, 7, 9, 10, 0));
        return dto;
    }
}
