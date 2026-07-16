package demo3.demo3_068.controller;

import demo3.demo3_068.common.PageResult;
import demo3.demo3_068.common.Result;
import demo3.demo3_068.dto.RefundRequestCreateDTO;
import demo3.demo3_068.dto.RefundRequestPageQueryDTO;
import demo3.demo3_068.dto.RefundRequestRejectDTO;
import demo3.demo3_068.service.RefundService;
import demo3.demo3_068.vo.RefundRequestVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/refund")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping("/request")
    public Result<Long> createRequest(@Valid @RequestBody RefundRequestCreateDTO createDTO) {
        return Result.success(refundService.createRequest(createDTO));
    }

    @GetMapping("/my/page")
    public Result<PageResult<RefundRequestVO>> myPage(@Valid RefundRequestPageQueryDTO queryDTO) {
        return Result.success(refundService.myPage(queryDTO));
    }

    @GetMapping("/{id}")
    public Result<RefundRequestVO> getDetail(@PathVariable Long id) {
        return Result.success(refundService.getDetail(id));
    }

    @GetMapping("/page")
    public Result<PageResult<RefundRequestVO>> page(@Valid RefundRequestPageQueryDTO queryDTO) {
        return Result.success(refundService.page(queryDTO));
    }

    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        refundService.approve(id);
        return Result.success();
    }

    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id,
                               @Valid @RequestBody RefundRequestRejectDTO rejectDTO) {
        refundService.reject(id, rejectDTO);
        return Result.success();
    }

    @PutMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id) {
        refundService.complete(id);
        return Result.success();
    }
}
