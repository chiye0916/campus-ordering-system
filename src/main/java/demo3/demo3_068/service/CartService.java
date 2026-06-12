package demo3.demo3_068.service;

import demo3.demo3_068.dto.CartAddDTO;
import demo3.demo3_068.dto.CartUpdateDTO;
import demo3.demo3_068.vo.CartVO;

import java.util.List;

public interface CartService {

    void add(CartAddDTO cartAddDTO);

    void update(CartUpdateDTO cartUpdateDTO);

    List<CartVO> list();

    void deleteByDishId(Long dishId);

    void clean();
}
