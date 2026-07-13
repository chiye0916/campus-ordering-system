package demo3.demo3_068.service;

import demo3.demo3_068.dto.MockPaymentCallbackDTO;

public interface PaymentService {

    void handleMockCallback(MockPaymentCallbackDTO callbackDTO);
}
