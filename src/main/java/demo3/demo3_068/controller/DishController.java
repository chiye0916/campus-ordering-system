package demo3.demo3_068.controller;

import demo3.demo3_068.common.PageResult;
import demo3.demo3_068.common.PermissionChecker;
import demo3.demo3_068.common.Result;
import demo3.demo3_068.dto.DishCreateDTO;
import demo3.demo3_068.dto.DishListQueryDTO;
import demo3.demo3_068.dto.DishPageQueryDTO;
import demo3.demo3_068.dto.DishStatusDTO;
import demo3.demo3_068.dto.DishUpdateDTO;
import demo3.demo3_068.service.DishService;
import demo3.demo3_068.vo.DishVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/dish")
public class DishController {

    private final DishService dishService;

    public DishController(DishService dishService) {
        this.dishService = dishService;
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody DishCreateDTO dishCreateDTO) {
        PermissionChecker.requireAdmin();
        return Result.success(dishService.create(dishCreateDTO));
    }

    @GetMapping("/page")
    public Result<PageResult<DishVO>> page(@Valid DishPageQueryDTO dishPageQueryDTO) {
        PermissionChecker.requireAdmin();
        return Result.success(dishService.page(dishPageQueryDTO));
    }

    @GetMapping("/list")
    public Result<List<DishVO>> list(@Valid DishListQueryDTO dishListQueryDTO) {
        return Result.success(dishService.list(dishListQueryDTO));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody DishUpdateDTO dishUpdateDTO) {
        PermissionChecker.requireAdmin();
        dishService.update(id, dishUpdateDTO);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody DishStatusDTO dishStatusDTO) {
        PermissionChecker.requireAdmin();
        dishService.updateStatus(id, dishStatusDTO);
        return Result.success();
    }
}
