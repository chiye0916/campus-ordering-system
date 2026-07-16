package demo3.demo3_068.service.impl;

import demo3.demo3_068.common.BaseContext;
import demo3.demo3_068.common.PageResult;
import demo3.demo3_068.common.PermissionChecker;
import demo3.demo3_068.dto.RefundRequestCreateDTO;
import demo3.demo3_068.dto.RefundRequestPageQueryDTO;
import demo3.demo3_068.dto.RefundRequestRejectDTO;
import demo3.demo3_068.entity.Orders;
import demo3.demo3_068.entity.RefundRequest;
import demo3.demo3_068.exception.BusinessException;
import demo3.demo3_068.mapper.OrdersMapper;
import demo3.demo3_068.mapper.RefundRequestMapper;
import demo3.demo3_068.model.OrderStatus;
import demo3.demo3_068.model.RefundRequestStatus;
import demo3.demo3_068.model.Role;
import demo3.demo3_068.service.RefundService;
import demo3.demo3_068.utils.RefundNoUtil;
import demo3.demo3_068.vo.RefundRequestVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RefundServiceImpl implements RefundService {

    private final RefundRequestMapper refundRequestMapper;
    private final OrdersMapper ordersMapper;

    public RefundServiceImpl(RefundRequestMapper refundRequestMapper, OrdersMapper ordersMapper) {
        this.refundRequestMapper = refundRequestMapper;
        this.ordersMapper = ordersMapper;
    }

    @Override
    @Transactional
    public Long createRequest(RefundRequestCreateDTO createDTO) {
        PermissionChecker.requireUser();
        Long userId = currentUserIdOrThrow();
        String reason = normalizeRequiredReason(createDTO.getReason(), "退款原因不能为空");

        Orders orders = ordersMapper.selectById(createDTO.getOrderId());
        if (orders == null || !userId.equals(orders.getUserId())) {
            throw new BusinessException(404, "订单不存在");
        }
        OrderStatus orderStatus = OrderStatus.fromCode(orders.getStatus());
        if (orderStatus != OrderStatus.PAID && orderStatus != OrderStatus.ACCEPTED) {
            throw new BusinessException("只有已支付或已接单订单才能申请退款");
        }
        if (refundRequestMapper.selectByOrderId(orders.getId()) != null) {
            throw new BusinessException("该订单已存在退款申请");
        }

        LocalDateTime now = LocalDateTime.now();
        RefundRequest refundRequest = new RefundRequest();
        refundRequest.setRefundNo(RefundNoUtil.generateRefundNo());
        refundRequest.setOrderId(orders.getId());
        refundRequest.setUserId(userId);
        refundRequest.setOrderNumber(orders.getNumber());
        refundRequest.setAmount(orders.getAmount());
        refundRequest.setReason(reason);
        refundRequest.setStatus(RefundRequestStatus.PENDING_REVIEW);
        refundRequest.setCreateTime(now);
        refundRequest.setUpdateTime(now);
        try {
            refundRequestMapper.insert(refundRequest);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("该订单已存在退款申请");
        }
        return refundRequest.getId();
    }

    @Override
    public PageResult<RefundRequestVO> myPage(RefundRequestPageQueryDTO queryDTO) {
        PermissionChecker.requireUser();
        return pageByScope(currentUserIdOrThrow(), queryDTO);
    }

    @Override
    public PageResult<RefundRequestVO> page(RefundRequestPageQueryDTO queryDTO) {
        PermissionChecker.requireAdmin();
        return pageByScope(null, queryDTO);
    }

    @Override
    public RefundRequestVO getDetail(Long id) {
        RefundRequest refundRequest = refundRequestMapper.selectById(id);
        if (refundRequest == null) {
            throw new BusinessException(404, "退款申请不存在");
        }
        Role role = PermissionChecker.currentRoleOrThrow();
        if (role == Role.ADMIN) {
            return toVO(refundRequest);
        }
        if (role == Role.USER && currentUserIdOrThrow().equals(refundRequest.getUserId())) {
            return toVO(refundRequest);
        }
        if (role == Role.USER) {
            throw new BusinessException(404, "退款申请不存在");
        }
        PermissionChecker.throwPermissionDenied();
        return null;
    }

    @Override
    @Transactional
    public void approve(Long id) {
        PermissionChecker.requireAdmin();
        Long reviewerId = currentUserIdOrThrow();
        RefundRequest refundRequest = getRequestOrThrow(id);
        if (refundRequest.getStatus() != RefundRequestStatus.PENDING_REVIEW) {
            throw new BusinessException("只有待审核退款申请才能审批通过");
        }
        LocalDateTime now = LocalDateTime.now();
        int orderRows = ordersMapper.updateStatusByIdInOldStatuses(
                refundRequest.getOrderId(),
                List.of(OrderStatus.PAID.getCode(), OrderStatus.ACCEPTED.getCode()),
                OrderStatus.REFUNDING.getCode());
        if (orderRows == 0) {
            throw new BusinessException("订单状态已变化，无法审批退款申请");
        }
        int requestRows = refundRequestMapper.approvePending(
                id,
                RefundRequestStatus.PENDING_REVIEW,
                RefundRequestStatus.APPROVED,
                reviewerId,
                now,
                now);
        if (requestRows == 0) {
            throw new BusinessException("退款申请状态已变化，请刷新后重试");
        }
    }

    @Override
    @Transactional
    public void reject(Long id, RefundRequestRejectDTO rejectDTO) {
        PermissionChecker.requireAdmin();
        Long reviewerId = currentUserIdOrThrow();
        String rejectReason = normalizeRequiredReason(rejectDTO.getRejectReason(), "拒绝原因不能为空");
        RefundRequest refundRequest = getRequestOrThrow(id);
        if (refundRequest.getStatus() != RefundRequestStatus.PENDING_REVIEW) {
            throw new BusinessException("只有待审核退款申请才能拒绝");
        }
        LocalDateTime now = LocalDateTime.now();
        int rows = refundRequestMapper.rejectPending(
                id,
                RefundRequestStatus.PENDING_REVIEW,
                RefundRequestStatus.REJECTED,
                reviewerId,
                now,
                rejectReason,
                now);
        if (rows == 0) {
            throw new BusinessException("退款申请状态已变化，请刷新后重试");
        }
    }

    @Override
    @Transactional
    public void complete(Long id) {
        PermissionChecker.requireAdmin();
        RefundRequest refundRequest = getRequestOrThrow(id);
        if (refundRequest.getStatus() != RefundRequestStatus.APPROVED) {
            throw new BusinessException("只有已通过退款申请才能完成退款");
        }
        LocalDateTime now = LocalDateTime.now();
        int orderRows = ordersMapper.updateStatusById(
                refundRequest.getOrderId(),
                OrderStatus.REFUNDING.getCode(),
                OrderStatus.REFUNDED.getCode());
        if (orderRows == 0) {
            throw new BusinessException("订单状态已变化，无法完成退款");
        }
        int requestRows = refundRequestMapper.completeApproved(
                id,
                RefundRequestStatus.APPROVED,
                RefundRequestStatus.COMPLETED,
                now,
                now);
        if (requestRows == 0) {
            throw new BusinessException("退款申请状态已变化，请刷新后重试");
        }
    }

    private PageResult<RefundRequestVO> pageByScope(Long userId, RefundRequestPageQueryDTO queryDTO) {
        RefundRequestStatus status = RefundRequestStatus.parse(queryDTO.getStatus());
        int offset = (queryDTO.getPage() - 1) * queryDTO.getPageSize();
        long total = refundRequestMapper.countPage(userId, status);
        List<RefundRequestVO> records = refundRequestMapper
                .selectPage(userId, status, offset, queryDTO.getPageSize())
                .stream()
                .map(this::toVO)
                .toList();
        return new PageResult<>(total, records);
    }

    private RefundRequest getRequestOrThrow(Long id) {
        RefundRequest refundRequest = refundRequestMapper.selectById(id);
        if (refundRequest == null) {
            throw new BusinessException(404, "退款申请不存在");
        }
        return refundRequest;
    }

    private String normalizeRequiredReason(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, message);
        }
        return value.trim();
    }

    private Long currentUserIdOrThrow() {
        Long userId = BaseContext.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        return userId;
    }

    private RefundRequestVO toVO(RefundRequest refundRequest) {
        return RefundRequestVO.builder()
                .id(refundRequest.getId())
                .refundNo(refundRequest.getRefundNo())
                .orderId(refundRequest.getOrderId())
                .userId(refundRequest.getUserId())
                .orderNumber(refundRequest.getOrderNumber())
                .amount(refundRequest.getAmount())
                .reason(refundRequest.getReason())
                .status(refundRequest.getStatus())
                .rejectReason(refundRequest.getRejectReason())
                .reviewerId(refundRequest.getReviewerId())
                .reviewTime(refundRequest.getReviewTime())
                .completeTime(refundRequest.getCompleteTime())
                .createTime(refundRequest.getCreateTime())
                .updateTime(refundRequest.getUpdateTime())
                .build();
    }
}
