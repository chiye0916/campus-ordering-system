package demo3.demo3_068.controller;

import demo3.demo3_068.common.PageResult;
import demo3.demo3_068.dto.RefundRequestCreateDTO;
import demo3.demo3_068.dto.RefundRequestPageQueryDTO;
import demo3.demo3_068.dto.RefundRequestRejectDTO;
import demo3.demo3_068.service.RefundService;
import demo3.demo3_068.vo.RefundRequestVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefundControllerTest {

    @Test
    void wrapsCreateListDetailAndReviewResponsesInResult() {
        RefundService refundService = mock(RefundService.class);
        RefundController controller = new RefundController(refundService);
        RefundRequestCreateDTO createDTO = new RefundRequestCreateDTO();
        RefundRequestPageQueryDTO pageDTO = new RefundRequestPageQueryDTO();
        RefundRequestRejectDTO rejectDTO = new RefundRequestRejectDTO();
        RefundRequestVO detail = RefundRequestVO.builder().id(501L).build();
        PageResult<RefundRequestVO> page = new PageResult<>(1L, List.of(detail));

        when(refundService.createRequest(createDTO)).thenReturn(501L);
        when(refundService.myPage(pageDTO)).thenReturn(page);
        when(refundService.getDetail(501L)).thenReturn(detail);
        when(refundService.page(pageDTO)).thenReturn(page);

        assertThat(controller.createRequest(createDTO).getData()).isEqualTo(501L);
        assertThat(controller.myPage(pageDTO).getData().getTotal()).isEqualTo(1L);
        assertThat(controller.getDetail(501L).getData().getId()).isEqualTo(501L);
        assertThat(controller.page(pageDTO).getData().getTotal()).isEqualTo(1L);
        assertThat(controller.approve(501L).getCode()).isEqualTo(200);
        assertThat(controller.reject(501L, rejectDTO).getCode()).isEqualTo(200);
        assertThat(controller.complete(501L).getCode()).isEqualTo(200);

        verify(refundService).approve(501L);
        verify(refundService).reject(501L, rejectDTO);
        verify(refundService).complete(501L);
    }
}
