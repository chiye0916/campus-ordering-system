package demo3.demo3_068.service;

import demo3.demo3_068.common.PageResult;
import demo3.demo3_068.dto.DishCreateDTO;
import demo3.demo3_068.dto.DishListQueryDTO;
import demo3.demo3_068.dto.DishPageQueryDTO;
import demo3.demo3_068.dto.DishStatusDTO;
import demo3.demo3_068.dto.DishUpdateDTO;
import demo3.demo3_068.vo.DishVO;

import java.util.List;

public interface DishService {

    Long create(DishCreateDTO dishCreateDTO);

    PageResult<DishVO> page(DishPageQueryDTO dishPageQueryDTO);

    List<DishVO> list(DishListQueryDTO dishListQueryDTO);

    void update(Long id, DishUpdateDTO dishUpdateDTO);

    void updateStatus(Long id, DishStatusDTO dishStatusDTO);
}
