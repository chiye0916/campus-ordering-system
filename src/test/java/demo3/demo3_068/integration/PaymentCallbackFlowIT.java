package demo3.demo3_068.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentCallbackFlowIT extends BaseIntegrationTest {

    @Test
    void successCallbackPaysOrderConfirmsStockAndDuplicateCallbackIsIdempotent() throws Exception {
        SubmittedPayment payment = submitAndStartPayment("pay_success_user", "pay-key-success", 5);

        JsonNode callback = postPaymentCallback(payment.tradeNo(), "CB-SUCCESS-1", "SUCCESS", "20.00");
        JsonNode duplicate = postPaymentCallback(payment.tradeNo(), "CB-SUCCESS-1", "SUCCESS", "20.00");

        assertThat(callback.path("code").asInt()).isEqualTo(200);
        assertThat(duplicate.path("code").asInt()).isEqualTo(200);
        assertThat(intCell("select status from orders where id = ?", payment.orderId())).isEqualTo(2);
        assertThat(intCell("select status from payment_record where trade_no = ?", payment.tradeNo())).isEqualTo(2);
        assertThat(longCell("select count(*) from payment_callback_record where callback_no = 'CB-SUCCESS-1'"))
                .isEqualTo(1L);
        assertThat(intCell("select available_stock from dish_stock where dish_id = ?", payment.dishId())).isEqualTo(3);
        assertThat(intCell("select locked_stock from dish_stock where dish_id = ?", payment.dishId())).isZero();
        assertThat(longCell("select count(*) from stock_record where order_id = ? and change_type = 'CONFIRM'",
                payment.orderId())).isEqualTo(1L);
        JsonNode history = getJsonWithToken("/order/" + payment.orderId() + "/status-history", payment.userToken())
                .path("data");
        assertThat(history).hasSize(2);
        assertThat(history.get(0).path("operation").asText()).isEqualTo("ORDER_SUBMIT");
        assertThat(history.get(0).path("oldStatus").isNull()).isTrue();
        assertThat(history.get(0).path("newStatus").asInt()).isEqualTo(1);
        assertThat(history.get(1).path("operation").asText()).isEqualTo("PAYMENT_SUCCESS");
        assertThat(history.get(1).path("oldStatus").asInt()).isEqualTo(1);
        assertThat(history.get(1).path("newStatus").asInt()).isEqualTo(2);
        assertThat(longCell("select count(*) from order_status_history where order_id = ?", payment.orderId()))
                .isEqualTo(2L);
    }

    @Test
    void amountMismatchRecordsTerminalResultWithoutPayingOrConfirmingStock() throws Exception {
        SubmittedPayment payment = submitAndStartPayment("pay_mismatch_user", "pay-key-mismatch", 5);

        JsonNode callback = postPaymentCallback(payment.tradeNo(), "CB-MISMATCH-1", "SUCCESS", "19.99");

        assertThat(callback.path("code").asInt()).isEqualTo(200);
        assertThat(intCell("select status from orders where id = ?", payment.orderId())).isEqualTo(1);
        assertThat(intCell("select status from payment_record where trade_no = ?", payment.tradeNo())).isEqualTo(1);
        assertThat(intCell("select process_status from payment_callback_record where callback_no = 'CB-MISMATCH-1'"))
                .isEqualTo(4);
        assertThat(intCell("select locked_stock from dish_stock where dish_id = ?", payment.dishId())).isEqualTo(2);
        assertThat(longCell("select count(*) from stock_record where order_id = ? and change_type = 'CONFIRM'",
                payment.orderId())).isZero();
    }

    @Test
    void failedCallbackMarksPaymentFailedButLeavesOrderPendingAndStockLocked() throws Exception {
        SubmittedPayment payment = submitAndStartPayment("pay_failed_user", "pay-key-failed", 5);

        JsonNode callback = postPaymentCallback(payment.tradeNo(), "CB-FAILED-1", "FAILED", "20.00");

        assertThat(callback.path("code").asInt()).isEqualTo(200);
        assertThat(intCell("select status from payment_record where trade_no = ?", payment.tradeNo())).isEqualTo(3);
        assertThat(intCell("select status from orders where id = ?", payment.orderId())).isEqualTo(1);
        assertThat(intCell("select available_stock from dish_stock where dish_id = ?", payment.dishId())).isEqualTo(3);
        assertThat(intCell("select locked_stock from dish_stock where dish_id = ?", payment.dishId())).isEqualTo(2);
        assertThat(longCell("select count(*) from stock_record where order_id = ? and change_type = 'RELEASE'",
                payment.orderId())).isZero();
        assertThat(longCell("select count(*) from stock_record where order_id = ? and change_type = 'CONFIRM'",
                payment.orderId())).isZero();
    }

    private SubmittedPayment submitAndStartPayment(String username, String idempotencyKey, int availableStock) throws Exception {
        createUser(username, "USER");
        String token = login(username);
        Long categoryId = createCategory("Payment Meals " + username);
        Long dishId = createDish(categoryId, "Payment Dish " + username, "10.00", 1);
        setStock(dishId, availableStock, 0);
        addCart(token, dishId, 2);
        Long orderId = submitOrder(token, idempotencyKey, "payment");
        JsonNode pay = startPayment(token, orderId);
        assertThat(pay.path("code").asInt()).isEqualTo(200);
        return new SubmittedPayment(orderId, dishId, pay.path("data").path("tradeNo").asText(), token);
    }

    private record SubmittedPayment(Long orderId, Long dishId, String tradeNo, String userToken) {
    }
}
