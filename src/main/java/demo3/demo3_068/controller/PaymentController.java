package demo3.demo3_068.controller;

import demo3.demo3_068.common.Result;
import demo3.demo3_068.dto.MockPaymentCallbackDTO;
import demo3.demo3_068.exception.PaymentCallbackRetryableException;
import demo3.demo3_068.model.MockPayStatus;
import demo3.demo3_068.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/mock/callback")
    public Result<Void> mockCallback(@Valid @RequestBody MockPaymentCallbackDTO callbackDTO) {
        if (!MockPayStatus.isSupported(callbackDTO.getPayStatus())) {
            return Result.error(400, "不支持的支付回调状态");
        }
        try {
            paymentService.handleMockCallback(callbackDTO);
            return Result.success();
        } catch (PaymentCallbackRetryableException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }
}
