package demo3.demo3_068.controller;

import demo3.demo3_068.common.PermissionChecker;
import demo3.demo3_068.common.Result;
import demo3.demo3_068.dto.CategoryCreateDTO;
import demo3.demo3_068.dto.CategoryUpdateDTO;
import demo3.demo3_068.service.CategoryService;
import demo3.demo3_068.vo.CategoryVO;
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
@RequestMapping("/category")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody CategoryCreateDTO categoryCreateDTO) {
        PermissionChecker.requireAdmin();
        return Result.success(categoryService.create(categoryCreateDTO));
    }

    @GetMapping("/list")
    public Result<List<CategoryVO>> list() {
        return Result.success(categoryService.list());
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody CategoryUpdateDTO categoryUpdateDTO) {
        PermissionChecker.requireAdmin();
        categoryService.update(id, categoryUpdateDTO);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        PermissionChecker.requireAdmin();
        categoryService.delete(id);
        return Result.success();
    }
}
