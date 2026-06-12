package demo3.demo3_068.service;

import demo3.demo3_068.common.PageResult;
import demo3.demo3_068.dto.OrderPageQueryDTO;
import demo3.demo3_068.dto.OrderSubmitDTO;
import demo3.demo3_068.vo.OrderDetailVO;
import demo3.demo3_068.vo.OrderPayVO;
import demo3.demo3_068.vo.OrderVO;

public interface OrderService {

    Long submit(OrderSubmitDTO orderSubmitDTO);

    OrderDetailVO getDetail(Long id);

    PageResult<OrderVO> page(OrderPageQueryDTO orderPageQueryDTO);

    OrderPayVO pay(Long id);

    void cancel(Long id);

    void complete(Long id);
}
