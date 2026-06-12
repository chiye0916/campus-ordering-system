package demo3.demo3_068.controller;

import demo3.demo3_068.common.PageResult;
import demo3.demo3_068.common.PermissionChecker;
import demo3.demo3_068.common.Result;
import demo3.demo3_068.dto.OrderPageQueryDTO;
import demo3.demo3_068.dto.OrderSubmitDTO;
import demo3.demo3_068.service.OrderService;
import demo3.demo3_068.vo.OrderDetailVO;
import demo3.demo3_068.vo.OrderPayVO;
import demo3.demo3_068.vo.OrderVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public Result<Long> submit(@Valid @RequestBody OrderSubmitDTO orderSubmitDTO) {
        return Result.success(orderService.submit(orderSubmitDTO));
    }

    @GetMapping("/{id}")
    public Result<OrderDetailVO> getDetail(@PathVariable Long id) {
        return Result.success(orderService.getDetail(id));
    }

    @GetMapping("/page")
    public Result<PageResult<OrderVO>> page(@Valid OrderPageQueryDTO orderPageQueryDTO) {
        return Result.success(orderService.page(orderPageQueryDTO));
    }

    @PutMapping("/{id}/pay")
    public Result<OrderPayVO> pay(@PathVariable Long id) {
        return Result.success(orderService.pay(id));
    }

    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        orderService.cancel(id);
        return Result.success();
    }

    @PutMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id) {
        PermissionChecker.requireAdmin();
        orderService.complete(id);
        return Result.success();
    }
}
