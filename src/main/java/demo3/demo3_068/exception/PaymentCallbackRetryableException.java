package demo3.demo3_068.exception;

public class PaymentCallbackRetryableException extends BusinessException {

    public PaymentCallbackRetryableException(String message) {
        super(409, message);
    }
}
