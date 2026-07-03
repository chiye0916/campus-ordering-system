package demo3.demo3_068.controller;

import demo3.demo3_068.dto.OrderSubmitDTO;
import demo3.demo3_068.exception.BusinessException;
import demo3.demo3_068.service.OrderService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class OrderControllerTest {

    @Test
    void submitRejectsMissingOrBlankIdempotencyKey() {
        OrderService orderService = mock(OrderService.class);
        OrderController controller = new OrderController(orderService);
        OrderSubmitDTO dto = new OrderSubmitDTO();

        assertThatThrownBy(() -> controller.submit(dto, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);

        assertThatThrownBy(() -> controller.submit(dto, "   "))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);

        verify(orderService, never()).submit(dto, "");
    }
}
