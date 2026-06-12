package demo3.demo3_068.service;

import demo3.demo3_068.dto.CategoryCreateDTO;
import demo3.demo3_068.dto.CategoryUpdateDTO;
import demo3.demo3_068.vo.CategoryVO;

import java.util.List;

public interface CategoryService {

    Long create(CategoryCreateDTO categoryCreateDTO);

    List<CategoryVO> list();

    void update(Long id, CategoryUpdateDTO categoryUpdateDTO);

    void delete(Long id);
}
