package demo3.demo3_068.controller;

import demo3.demo3_068.common.PermissionChecker;
import demo3.demo3_068.common.Result;
import demo3.demo3_068.dto.CartAddDTO;
import demo3.demo3_068.dto.CartUpdateDTO;
import demo3.demo3_068.service.CartService;
import demo3.demo3_068.vo.CartVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping("/add")
    public Result<Void> add(@Valid @RequestBody CartAddDTO cartAddDTO) {
        PermissionChecker.requireUser();
        cartService.add(cartAddDTO);
        return Result.success();
    }

    @PutMapping("/update")
    public Result<Void> update(@Valid @RequestBody CartUpdateDTO cartUpdateDTO) {
        PermissionChecker.requireUser();
        cartService.update(cartUpdateDTO);
        return Result.success();
    }

    @GetMapping("/list")
    public Result<List<CartVO>> list() {
        PermissionChecker.requireUser();
        return Result.success(cartService.list());
    }

    @DeleteMapping("/clean")
    public Result<Void> clean() {
        PermissionChecker.requireUser();
        cartService.clean();
        return Result.success();
    }

    @DeleteMapping("/{dishId}")
    public Result<Void> deleteByDishId(@PathVariable Long dishId) {
        PermissionChecker.requireUser();
        cartService.deleteByDishId(dishId);
        return Result.success();
    }
}
