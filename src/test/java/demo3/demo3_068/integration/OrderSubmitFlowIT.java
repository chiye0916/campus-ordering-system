package demo3.demo3_068.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderSubmitFlowIT extends BaseIntegrationTest {

    @Test
    void submitOrderCreatesRowsLocksStockAndDuplicateKeyDoesNotRepeatSideEffects() throws Exception {
        createUser("order_user", "USER");
        String token = login("order_user");
        Long categoryId = createCategory("Meals");
        Long dishId = createDish(categoryId, "Chicken Set", "20.00", 1);
        setStock(dishId, 10, 0);
        addCart(token, dishId, 2);

        Long orderId = submitOrder(token, "order-key-1", "lunch");
        Long duplicateOrderId = submitOrder(token, "order-key-1", "lunch");

        assertThat(duplicateOrderId).isEqualTo(orderId);
        assertThat(longCell("select count(*) from orders where id = ?", orderId)).isEqualTo(1L);
        assertThat(longCell("select count(*) from order_detail where order_id = ?", orderId)).isEqualTo(1L);
        assertThat(intCell("select quantity from order_detail where order_id = ?", orderId)).isEqualTo(2);
        assertThat(intCell("select available_stock from dish_stock where dish_id = ?", dishId)).isEqualTo(8);
        assertThat(intCell("select locked_stock from dish_stock where dish_id = ?", dishId)).isEqualTo(2);
        assertThat(longCell("select count(*) from orders")).isEqualTo(1L);
        assertThat(longCell("select count(*) from stock_record where order_id = ? and change_type = 'LOCK'", orderId))
                .isEqualTo(1L);
    }

    @Test
    void insufficientStockRejectsOrderWithoutCommittedRowsOrNegativeStock() throws Exception {
        createUser("order_stock_user", "USER");
        String token = login("order_stock_user");
        Long categoryId = createCategory("Snacks");
        Long dishId = createDish(categoryId, "Cookie", "5.00", 1);
        setStock(dishId, 1, 0);
        addCart(token, dishId, 3);

        JsonNode response = postJsonWithTokenAndHeader(
                "/order/submit",
                token,
                java.util.Map.of("remark", "too much"),
                "Idempotency-Key",
                "order-key-insufficient");

        assertThat(response.path("code").asInt()).isNotEqualTo(200);
        assertThat(longCell("select count(*) from orders")).isZero();
        assertThat(longCell("select count(*) from order_detail")).isZero();
        assertThat(intCell("select available_stock from dish_stock where dish_id = ?", dishId)).isEqualTo(1);
        assertThat(intCell("select locked_stock from dish_stock where dish_id = ?", dishId)).isZero();
    }
}
