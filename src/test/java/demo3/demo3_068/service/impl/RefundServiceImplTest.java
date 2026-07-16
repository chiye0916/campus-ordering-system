package demo3.demo3_068.service.impl;

import demo3.demo3_068.common.BaseContext;
import demo3.demo3_068.common.PageResult;
import demo3.demo3_068.dto.RefundRequestCreateDTO;
import demo3.demo3_068.dto.RefundRequestPageQueryDTO;
import demo3.demo3_068.dto.RefundRequestRejectDTO;
import demo3.demo3_068.entity.Orders;
import demo3.demo3_068.entity.RefundRequest;
import demo3.demo3_068.exception.BusinessException;
import demo3.demo3_068.mapper.OrdersMapper;
import demo3.demo3_068.mapper.RefundRequestMapper;
import demo3.demo3_068.model.OrderStatus;
import demo3.demo3_068.model.OrderStatusChangeOperation;
import demo3.demo3_068.model.RefundRequestStatus;
import demo3.demo3_068.model.Role;
import demo3.demo3_068.service.OrderStatusHistoryService;
import demo3.demo3_068.vo.RefundRequestVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceImplTest {

    @Mock
    private RefundRequestMapper refundRequestMapper;
    @Mock
    private OrdersMapper ordersMapper;
    @Mock
    private OrderStatusHistoryService orderStatusHistoryService;

    private RefundServiceImpl refundService;

    @BeforeEach
    void setUp() {
        refundService = new RefundServiceImpl(refundRequestMapper, ordersMapper, orderStatusHistoryService);
        BaseContext.setCurrentUserId(7L);
        BaseContext.setCurrentUserRole(Role.USER);
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void userCreatesRefundRequestForOwnPaidOrderFromServerSnapshot() {
        Orders order = order(OrderStatus.PAID, 7L);
        when(ordersMapper.selectById(101L)).thenReturn(order);
        when(refundRequestMapper.selectByOrderId(101L)).thenReturn(null);
        when(refundRequestMapper.insert(any(RefundRequest.class))).thenAnswer(invocation -> {
            RefundRequest refundRequest = invocation.getArgument(0);
            refundRequest.setId(501L);
            return 1;
        });

        Long id = refundService.createRequest(createDTO(101L, " 不想要了 "));

        assertThat(id).isEqualTo(501L);
        ArgumentCaptor<RefundRequest> captor = ArgumentCaptor.forClass(RefundRequest.class);
        verify(refundRequestMapper).insert(captor.capture());
        RefundRequest saved = captor.getValue();
        assertThat(saved.getRefundNo()).startsWith("RF");
        assertThat(saved.getOrderId()).isEqualTo(101L);
        assertThat(saved.getOrderNumber()).isEqualTo("202607080001");
        assertThat(saved.getAmount()).isEqualByComparingTo("30.00");
        assertThat(saved.getReason()).isEqualTo("不想要了");
        assertThat(saved.getStatus()).isEqualTo(RefundRequestStatus.PENDING_REVIEW);
        verify(ordersMapper, never()).updateStatusById(any(), any(), any());
    }

    @Test
    void userCreatesRefundRequestForOwnAcceptedOrder() {
        when(ordersMapper.selectById(101L)).thenReturn(order(OrderStatus.ACCEPTED, 7L));
        when(refundRequestMapper.selectByOrderId(101L)).thenReturn(null);
        when(refundRequestMapper.insert(any(RefundRequest.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, RefundRequest.class).setId(502L);
            return 1;
        });

        assertThat(refundService.createRequest(createDTO(101L, "配送太慢"))).isEqualTo(502L);
    }

    @Test
    void createRejectsWrongActorOwnershipStatusBlankReasonAndDuplicate() {
        BaseContext.setCurrentUserRole(Role.ADMIN);
        assertThatThrownBy(() -> refundService.createRequest(createDTO(101L, "原因")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);

        BaseContext.setCurrentUserRole(Role.USER);
        when(ordersMapper.selectById(101L)).thenReturn(order(OrderStatus.PAID, 8L));
        assertThatThrownBy(() -> refundService.createRequest(createDTO(101L, "原因")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(404);

        when(ordersMapper.selectById(102L)).thenReturn(order(102L, OrderStatus.COMPLETED, 7L));
        assertThatThrownBy(() -> refundService.createRequest(createDTO(102L, "原因")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只有已支付或已接单订单才能申请退款");

        assertThatThrownBy(() -> refundService.createRequest(createDTO(102L, " ")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);

        RefundRequest existing = refundRequest(601L, 7L, RefundRequestStatus.REJECTED);
        when(ordersMapper.selectById(103L)).thenReturn(order(103L, OrderStatus.PAID, 7L));
        when(refundRequestMapper.selectByOrderId(103L)).thenReturn(existing);
        assertThatThrownBy(() -> refundService.createRequest(createDTO(103L, "原因")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该订单已存在退款申请");
    }

    @Test
    void createTranslatesDuplicateKeyToBusinessError() {
        when(ordersMapper.selectById(101L)).thenReturn(order(OrderStatus.PAID, 7L));
        when(refundRequestMapper.selectByOrderId(101L)).thenReturn(null);
        when(refundRequestMapper.insert(any(RefundRequest.class))).thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> refundService.createRequest(createDTO(101L, "原因")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该订单已存在退款申请");
    }

    @Test
    void userAndAdminVisibilityRulesApplyToPageAndDetail() {
        RefundRequest mine = refundRequest(501L, 7L, RefundRequestStatus.PENDING_REVIEW);
        when(refundRequestMapper.countPage(7L, RefundRequestStatus.PENDING_REVIEW)).thenReturn(1L);
        when(refundRequestMapper.selectPage(7L, RefundRequestStatus.PENDING_REVIEW, 0, 10)).thenReturn(List.of(mine));

        PageResult<RefundRequestVO> myPage = refundService.myPage(pageQuery("PENDING_REVIEW"));

        assertThat(myPage.getTotal()).isEqualTo(1L);
        assertThat(myPage.getRecords()).hasSize(1);

        when(refundRequestMapper.selectById(501L)).thenReturn(mine);
        assertThat(refundService.getDetail(501L).getUserId()).isEqualTo(7L);

        RefundRequest another = refundRequest(502L, 8L, RefundRequestStatus.PENDING_REVIEW);
        when(refundRequestMapper.selectById(502L)).thenReturn(another);
        assertThatThrownBy(() -> refundService.getDetail(502L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(404);

        BaseContext.setCurrentUserRole(Role.ADMIN);
        when(refundRequestMapper.countPage(null, null)).thenReturn(2L);
        when(refundRequestMapper.selectPage(null, null, 0, 10)).thenReturn(List.of(mine, another));
        assertThat(refundService.page(pageQuery(null)).getTotal()).isEqualTo(2L);
        assertThat(refundService.getDetail(502L).getUserId()).isEqualTo(8L);

        BaseContext.setCurrentUserRole(Role.MERCHANT);
        assertThatThrownBy(() -> refundService.getDetail(501L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);
    }

    @Test
    void adminApproveRejectAndCompleteSuccessPaths() {
        BaseContext.setCurrentUserId(99L);
        BaseContext.setCurrentUserRole(Role.ADMIN);
        RefundRequest pending = refundRequest(501L, 7L, RefundRequestStatus.PENDING_REVIEW);
        when(refundRequestMapper.selectById(501L)).thenReturn(pending);
        when(ordersMapper.selectById(101L)).thenReturn(order(OrderStatus.PAID, 7L));
        when(ordersMapper.updateStatusByIdInOldStatuses(
                eq(101L),
                eq(List.of(OrderStatus.PAID.getCode(), OrderStatus.ACCEPTED.getCode())),
                eq(OrderStatus.REFUNDING.getCode()))).thenReturn(1);
        when(refundRequestMapper.approvePending(eq(501L), eq(RefundRequestStatus.PENDING_REVIEW),
                eq(RefundRequestStatus.APPROVED), eq(99L), any(), any())).thenReturn(1);

        refundService.approve(501L);

        verify(refundRequestMapper).approvePending(eq(501L), eq(RefundRequestStatus.PENDING_REVIEW),
                eq(RefundRequestStatus.APPROVED), eq(99L), any(), any());
        verify(orderStatusHistoryService).recordChange(any(Orders.class), eq(OrderStatus.PAID.getCode()), eq(OrderStatus.REFUNDING),
                eq(OrderStatusChangeOperation.REFUND_REQUEST_APPROVE), eq(99L), eq(Role.ADMIN), eq("退款申请审核通过：RF202607080001"));

        RefundRequest pendingForReject = refundRequest(502L, 7L, RefundRequestStatus.PENDING_REVIEW);
        when(refundRequestMapper.selectById(502L)).thenReturn(pendingForReject);
        when(refundRequestMapper.rejectPending(eq(502L), eq(RefundRequestStatus.PENDING_REVIEW),
                eq(RefundRequestStatus.REJECTED), eq(99L), any(), eq("材料不足"), any())).thenReturn(1);
        refundService.reject(502L, rejectDTO(" 材料不足 "));
        verify(refundRequestMapper).rejectPending(eq(502L), eq(RefundRequestStatus.PENDING_REVIEW),
                eq(RefundRequestStatus.REJECTED), eq(99L), any(), eq("材料不足"), any());
        verify(orderStatusHistoryService, never()).recordChange(any(), any(), eq(OrderStatus.REFUNDED), any(), any(), any(), any());

        RefundRequest approved = refundRequest(503L, 7L, RefundRequestStatus.APPROVED);
        when(refundRequestMapper.selectById(503L)).thenReturn(approved);
        when(ordersMapper.selectById(101L)).thenReturn(order(OrderStatus.REFUNDING, 7L));
        when(ordersMapper.updateStatusById(101L, OrderStatus.REFUNDING.getCode(), OrderStatus.REFUNDED.getCode()))
                .thenReturn(1);
        when(refundRequestMapper.completeApproved(eq(503L), eq(RefundRequestStatus.APPROVED),
                eq(RefundRequestStatus.COMPLETED), any(), any())).thenReturn(1);
        refundService.complete(503L);
        verify(refundRequestMapper).completeApproved(eq(503L), eq(RefundRequestStatus.APPROVED),
                eq(RefundRequestStatus.COMPLETED), any(), any());
        verify(orderStatusHistoryService).recordChange(any(Orders.class), eq(OrderStatus.REFUNDING.getCode()), eq(OrderStatus.REFUNDED),
                eq(OrderStatusChangeOperation.REFUND_REQUEST_COMPLETE), eq(99L), eq(Role.ADMIN), eq("退款申请完成退款：RF202607080001"));
    }

    @Test
    void repeatedOrChangedStatusReviewOperationsFailClearly() {
        BaseContext.setCurrentUserRole(Role.ADMIN);
        when(refundRequestMapper.selectById(501L)).thenReturn(refundRequest(501L, 7L, RefundRequestStatus.APPROVED));
        assertThatThrownBy(() -> refundService.approve(501L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只有待审核退款申请才能审批通过");

        when(refundRequestMapper.selectById(502L)).thenReturn(refundRequest(502L, 7L, RefundRequestStatus.PENDING_REVIEW));
        when(ordersMapper.selectById(101L)).thenReturn(order(OrderStatus.PAID, 7L));
        when(ordersMapper.updateStatusByIdInOldStatuses(any(), any(), any())).thenReturn(0);
        assertThatThrownBy(() -> refundService.approve(502L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单状态已变化，无法审批退款申请");

        when(refundRequestMapper.selectById(503L)).thenReturn(refundRequest(503L, 7L, RefundRequestStatus.PENDING_REVIEW));
        assertThatThrownBy(() -> refundService.complete(503L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只有已通过退款申请才能完成退款");

        when(refundRequestMapper.selectById(504L)).thenReturn(refundRequest(504L, 7L, RefundRequestStatus.APPROVED));
        when(ordersMapper.selectById(101L)).thenReturn(order(OrderStatus.REFUNDING, 7L));
        when(ordersMapper.updateStatusById(any(), any(), any())).thenReturn(0);
        assertThatThrownBy(() -> refundService.complete(504L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("订单状态已变化，无法完成退款");
    }

    @Test
    void nonAdminRolesCannotReviewRefundRequests() {
        for (Role role : List.of(Role.USER, Role.MERCHANT, Role.DELIVERY, Role.SYSTEM)) {
            BaseContext.setCurrentUserRole(role);
            assertThatThrownBy(() -> refundService.approve(501L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(403);
            assertThatThrownBy(() -> refundService.reject(501L, rejectDTO("原因")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(403);
            assertThatThrownBy(() -> refundService.complete(501L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(403);
        }
    }

    @Test
    void refundRequestFlowDoesNotUseStockPersistenceCollaborators() {
        BaseContext.setCurrentUserRole(Role.ADMIN);
        when(refundRequestMapper.selectById(501L)).thenReturn(refundRequest(501L, 7L, RefundRequestStatus.APPROVED));
        when(ordersMapper.selectById(101L)).thenReturn(order(OrderStatus.REFUNDING, 7L));
        when(ordersMapper.updateStatusById(101L, OrderStatus.REFUNDING.getCode(), OrderStatus.REFUNDED.getCode()))
                .thenReturn(1);
        when(refundRequestMapper.completeApproved(eq(501L), eq(RefundRequestStatus.APPROVED),
                eq(RefundRequestStatus.COMPLETED), any(), any())).thenReturn(1);

        refundService.complete(501L);

        verify(ordersMapper).updateStatusById(101L, OrderStatus.REFUNDING.getCode(), OrderStatus.REFUNDED.getCode());
    }

    private RefundRequestCreateDTO createDTO(Long orderId, String reason) {
        RefundRequestCreateDTO dto = new RefundRequestCreateDTO();
        dto.setOrderId(orderId);
        dto.setReason(reason);
        return dto;
    }

    private RefundRequestRejectDTO rejectDTO(String reason) {
        RefundRequestRejectDTO dto = new RefundRequestRejectDTO();
        dto.setRejectReason(reason);
        return dto;
    }

    private RefundRequestPageQueryDTO pageQuery(String status) {
        RefundRequestPageQueryDTO dto = new RefundRequestPageQueryDTO();
        dto.setPage(1);
        dto.setPageSize(10);
        dto.setStatus(status);
        return dto;
    }

    private Orders order(OrderStatus status, Long userId) {
        return order(101L, status, userId);
    }

    private Orders order(Long id, OrderStatus status, Long userId) {
        Orders orders = new Orders();
        orders.setId(id);
        orders.setNumber("202607080001");
        orders.setUserId(userId);
        orders.setStatus(status.getCode());
        orders.setAmount(new BigDecimal("30.00"));
        return orders;
    }

    private RefundRequest refundRequest(Long id, Long userId, RefundRequestStatus status) {
        RefundRequest refundRequest = new RefundRequest();
        refundRequest.setId(id);
        refundRequest.setRefundNo("RF202607080001");
        refundRequest.setOrderId(101L);
        refundRequest.setUserId(userId);
        refundRequest.setOrderNumber("202607080001");
        refundRequest.setAmount(new BigDecimal("30.00"));
        refundRequest.setReason("原因");
        refundRequest.setStatus(status);
        return refundRequest;
    }
}
