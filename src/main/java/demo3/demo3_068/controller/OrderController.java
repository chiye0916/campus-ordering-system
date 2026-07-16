package demo3.demo3_068.controller;

import demo3.demo3_068.common.PageResult;
import demo3.demo3_068.common.PermissionChecker;
import demo3.demo3_068.common.Result;
import demo3.demo3_068.dto.OrderPageQueryDTO;
import demo3.demo3_068.dto.OrderSubmitDTO;
import demo3.demo3_068.exception.BusinessException;
import demo3.demo3_068.service.OrderService;
import demo3.demo3_068.vo.OrderDetailVO;
import demo3.demo3_068.vo.OrderPayVO;
import demo3.demo3_068.vo.OrderStatusHistoryVO;
import demo3.demo3_068.vo.OrderVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/order")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/submit")
    public Result<Long> submit(@Valid @RequestBody OrderSubmitDTO orderSubmitDTO,
                               @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        PermissionChecker.requireUser();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(400, "Idempotency-Key 不能为空");
        }
        return Result.success(orderService.submit(orderSubmitDTO, idempotencyKey.trim()));
    }

    @GetMapping("/{id}")
    public Result<OrderDetailVO> getDetail(@PathVariable Long id) {
        return Result.success(orderService.getDetail(id));
    }

    @GetMapping("/{id}/status-history")
    public Result<java.util.List<OrderStatusHistoryVO>> getStatusHistory(@PathVariable Long id) {
        return Result.success(orderService.getStatusHistory(id));
    }

    @GetMapping("/page")
    public Result<PageResult<OrderVO>> page(@Valid OrderPageQueryDTO orderPageQueryDTO) {
        return Result.success(orderService.page(orderPageQueryDTO));
    }

    @PutMapping("/{id}/pay")
    public Result<OrderPayVO> pay(@PathVariable Long id) {
        PermissionChecker.requireUser();
        return Result.success(orderService.pay(id));
    }

    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        PermissionChecker.requireUser();
        orderService.cancel(id);
        return Result.success();
    }

    @PutMapping("/{id}/accept")
    public Result<Void> accept(@PathVariable Long id) {
        PermissionChecker.requireMerchantOrAdmin();
        orderService.accept(id);
        return Result.success();
    }

    @PutMapping("/{id}/delivery/start")
    public Result<Void> startDelivery(@PathVariable Long id) {
        PermissionChecker.requireDeliveryOrAdmin();
        orderService.startDelivery(id);
        return Result.success();
    }

    @PutMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id) {
        PermissionChecker.requireDeliveryOrAdmin();
        orderService.complete(id);
        return Result.success();
    }

    @PutMapping("/{id}/refund/start")
    public Result<Void> startRefund(@PathVariable Long id) {
        PermissionChecker.requireAdmin();
        orderService.startRefund(id);
        return Result.success();
    }

    @PutMapping("/{id}/refund/complete")
    public Result<Void> completeRefund(@PathVariable Long id) {
        PermissionChecker.requireAdmin();
        orderService.completeRefund(id);
        return Result.success();
    }
}
