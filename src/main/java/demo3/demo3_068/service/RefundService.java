package demo3.demo3_068.service;

import demo3.demo3_068.common.PageResult;
import demo3.demo3_068.dto.RefundRequestCreateDTO;
import demo3.demo3_068.dto.RefundRequestPageQueryDTO;
import demo3.demo3_068.dto.RefundRequestRejectDTO;
import demo3.demo3_068.vo.RefundRequestVO;

public interface RefundService {

    Long createRequest(RefundRequestCreateDTO createDTO);

    PageResult<RefundRequestVO> myPage(RefundRequestPageQueryDTO queryDTO);

    PageResult<RefundRequestVO> page(RefundRequestPageQueryDTO queryDTO);

    RefundRequestVO getDetail(Long id);

    void approve(Long id);

    void reject(Long id, RefundRequestRejectDTO rejectDTO);

    void complete(Long id);
}
