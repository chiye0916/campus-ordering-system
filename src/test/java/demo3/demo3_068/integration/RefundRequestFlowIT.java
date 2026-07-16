package demo3.demo3_068.integration;

import com.fasterxml.jackson.databind.JsonNode;
import demo3.demo3_068.model.OrderStatus;
import demo3.demo3_068.model.RefundRequestStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RefundRequestFlowIT extends BaseIntegrationTest {

    @Test
    void userRequestAdminApproveAndCompleteMovesRequestAndOrderWithoutStockRecords() throws Exception {
        Long userId = createUser("refund_user", "USER");
        createUser("refund_admin", "ADMIN");
        String userToken = login("refund_user");
        String adminToken = login("refund_admin");
        Long orderId = createPaidOrder(userId);

        JsonNode createResponse = postJsonWithToken("/refund/request", userToken, Map.of(
                "orderId", orderId,
                "reason", "不想要了"));
        Long refundRequestId = createResponse.path("data").asLong();

        assertThat(createResponse.path("code").asInt()).isEqualTo(200);
        assertThat(stringCell("select status from refund_request where id = ?", refundRequestId))
                .isEqualTo(RefundRequestStatus.PENDING_REVIEW.name());
        assertThat(decimalCell("select amount from refund_request where id = ?", refundRequestId))
                .isEqualByComparingTo("30.00");
        assertThat(intCell("select status from orders where id = ?", orderId)).isEqualTo(OrderStatus.PAID.getCode());

        JsonNode approveResponse = putJsonWithToken("/refund/" + refundRequestId + "/approve", adminToken, Map.of());

        assertThat(approveResponse.path("code").asInt()).isEqualTo(200);
        assertThat(stringCell("select status from refund_request where id = ?", refundRequestId))
                .isEqualTo(RefundRequestStatus.APPROVED.name());
        assertThat(intCell("select status from orders where id = ?", orderId)).isEqualTo(OrderStatus.REFUNDING.getCode());

        JsonNode completeResponse = putJsonWithToken("/refund/" + refundRequestId + "/complete", adminToken, Map.of());

        assertThat(completeResponse.path("code").asInt()).isEqualTo(200);
        assertThat(stringCell("select status from refund_request where id = ?", refundRequestId))
                .isEqualTo(RefundRequestStatus.COMPLETED.name());
        assertThat(intCell("select status from orders where id = ?", orderId)).isEqualTo(OrderStatus.REFUNDED.getCode());
        assertThat(longCell("select count(*) from stock_record where order_id = ?", orderId)).isZero();
    }

    private Long createPaidOrder(Long userId) {
        jdbcTemplate.update("""
                insert into orders (number, user_id, status, amount, remark, order_time, pay_time)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                "ORDER-REFUND-001",
                userId,
                OrderStatus.PAID.getCode(),
                new BigDecimal("30.00"),
                "refund integration",
                LocalDateTime.now(),
                LocalDateTime.now());
        return longCell("select id from orders where number = ?", "ORDER-REFUND-001");
    }
}
