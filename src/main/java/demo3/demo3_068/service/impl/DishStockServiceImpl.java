package demo3.demo3_068.service.impl;

import demo3.demo3_068.common.BaseContext;
import demo3.demo3_068.common.PermissionChecker;
import demo3.demo3_068.dto.DishStockSetDTO;
import demo3.demo3_068.entity.Dish;
import demo3.demo3_068.entity.DishStock;
import demo3.demo3_068.entity.StockRecord;
import demo3.demo3_068.exception.BusinessException;
import demo3.demo3_068.mapper.DishMapper;
import demo3.demo3_068.mapper.DishStockMapper;
import demo3.demo3_068.mapper.StockRecordMapper;
import demo3.demo3_068.model.StockChangeType;
import demo3.demo3_068.service.DishStockService;
import demo3.demo3_068.vo.DishStockVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;

@Service
public class DishStockServiceImpl implements DishStockService {

    private final DishStockMapper dishStockMapper;
    private final StockRecordMapper stockRecordMapper;
    private final DishMapper dishMapper;

    public DishStockServiceImpl(DishStockMapper dishStockMapper,
                                StockRecordMapper stockRecordMapper,
                                DishMapper dishMapper) {
        this.dishStockMapper = dishStockMapper;
        this.stockRecordMapper = stockRecordMapper;
        this.dishMapper = dishMapper;
    }

    @Override
    public DishStockVO getStock(Long dishId) {
        ensureDishExists(dishId);
        DishStock dishStock = dishStockMapper.selectByDishId(dishId);
        if (dishStock == null) {
            throw new BusinessException("商品库存未初始化");
        }
        return toDishStockVO(dishStock);
    }

    @Override
    @Transactional
    public void setStock(Long dishId, DishStockSetDTO dishStockSetDTO) {
        PermissionChecker.requireMerchantOrAdmin();
        Long operatorId = getCurrentUserIdOrThrow();
        Integer availableStock = dishStockSetDTO.getAvailableStock();
        if (availableStock == null || availableStock < 0) {
            throw new BusinessException("可用库存不能小于0");
        }

        ensureDishExists(dishId);
        DishStock lockedStock = dishStockMapper.selectByDishIdForUpdate(dishId);
        if (lockedStock == null) {
            insertInitialStockRow(dishId);
            lockedStock = dishStockMapper.selectByDishIdForUpdate(dishId);
        }
        if (lockedStock == null) {
            throw new BusinessException("商品库存初始化失败，请重试");
        }

        int availableBefore = lockedStock.getAvailableStock();
        int lockedBefore = lockedStock.getLockedStock();
        int rows = dishStockMapper.updateAvailableStockByDishId(dishId, availableStock);
        if (rows == 0) {
            throw new BusinessException("设置库存失败，请重试");
        }
        writeRecord(
                dishId,
                null,
                StockChangeType.SET,
                availableStock - availableBefore,
                availableBefore,
                availableStock,
                lockedBefore,
                lockedBefore,
                operatorId,
                dishStockSetDTO.getRemark());
    }

    @Override
    @Transactional
    public void lockStock(Long orderId, Map<Long, Integer> dishQuantities, Long operatorId) {
        operateOrderStock(orderId, dishQuantities, operatorId, StockChangeType.LOCK, "订单提交锁定库存");
    }

    @Override
    @Transactional
    public void confirmLockedStock(Long orderId, Map<Long, Integer> dishQuantities, Long operatorId) {
        operateOrderStock(orderId, dishQuantities, operatorId, StockChangeType.CONFIRM, "支付成功确认库存");
    }

    @Override
    @Transactional
    public void releaseLockedStock(Long orderId, Map<Long, Integer> dishQuantities, Long operatorId) {
        operateOrderStock(orderId, dishQuantities, operatorId, StockChangeType.RELEASE, "取消订单释放库存");
    }

    @Override
    @Transactional
    public void releaseLockedStock(Long orderId, Map<Long, Integer> dishQuantities, Long operatorId, String remark) {
        operateOrderStock(orderId, dishQuantities, operatorId, StockChangeType.RELEASE, remark);
    }

    private void operateOrderStock(Long orderId,
                                   Map<Long, Integer> dishQuantities,
                                   Long operatorId,
                                   StockChangeType changeType,
                                   String remark) {
        if (dishQuantities == null || dishQuantities.isEmpty()) {
            return;
        }
        new TreeMap<>(dishQuantities).forEach((dishId, quantity) ->
                operateSingleOrderStock(orderId, dishId, quantity, operatorId, changeType, remark));
    }

    private void operateSingleOrderStock(Long orderId,
                                         Long dishId,
                                         Integer quantity,
                                         Long operatorId,
                                         StockChangeType changeType,
                                         String remark) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException("库存变更数量必须大于0");
        }
        DishStock lockedStock = dishStockMapper.selectByDishIdForUpdate(dishId);
        if (lockedStock == null) {
            throw new BusinessException("商品库存未初始化");
        }

        int availableBefore = lockedStock.getAvailableStock();
        int lockedBefore = lockedStock.getLockedStock();
        int availableAfter = availableBefore;
        int lockedAfter = lockedBefore;
        int rows;

        if (changeType == StockChangeType.LOCK) {
            rows = dishStockMapper.lockStock(dishId, quantity);
            availableAfter = availableBefore - quantity;
            lockedAfter = lockedBefore + quantity;
            if (rows == 0) {
                throw new BusinessException("商品库存不足");
            }
        } else if (changeType == StockChangeType.CONFIRM) {
            rows = dishStockMapper.confirmLockedStock(dishId, quantity);
            lockedAfter = lockedBefore - quantity;
            if (rows == 0) {
                throw new BusinessException("锁定库存不足，无法确认");
            }
        } else if (changeType == StockChangeType.RELEASE) {
            rows = dishStockMapper.releaseLockedStock(dishId, quantity);
            availableAfter = availableBefore + quantity;
            lockedAfter = lockedBefore - quantity;
            if (rows == 0) {
                throw new BusinessException("锁定库存不足，无法释放");
            }
        } else {
            throw new BusinessException("不支持的库存变更类型");
        }

        writeRecord(
                dishId,
                orderId,
                changeType,
                quantity,
                availableBefore,
                availableAfter,
                lockedBefore,
                lockedAfter,
                operatorId,
                remark);
    }

    private void insertInitialStockRow(Long dishId) {
        DishStock dishStock = new DishStock();
        dishStock.setDishId(dishId);
        dishStock.setAvailableStock(0);
        dishStock.setLockedStock(0);
        dishStock.setVersion(0);
        try {
            dishStockMapper.insert(dishStock);
        } catch (DuplicateKeyException ignored) {
            // Another transaction initialized the stock row first; the following for-update read will use it.
        }
    }

    private void ensureDishExists(Long dishId) {
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null) {
            throw new BusinessException(404, "商品不存在");
        }
    }

    private Long getCurrentUserIdOrThrow() {
        Long userId = BaseContext.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        return userId;
    }

    private void writeRecord(Long dishId,
                             Long orderId,
                             StockChangeType changeType,
                             Integer changeQuantity,
                             Integer availableBefore,
                             Integer availableAfter,
                             Integer lockedBefore,
                             Integer lockedAfter,
                             Long operatorId,
                             String remark) {
        StockRecord stockRecord = new StockRecord();
        stockRecord.setDishId(dishId);
        stockRecord.setOrderId(orderId);
        stockRecord.setChangeType(changeType.name());
        stockRecord.setChangeQuantity(changeQuantity);
        stockRecord.setAvailableBefore(availableBefore);
        stockRecord.setAvailableAfter(availableAfter);
        stockRecord.setLockedBefore(lockedBefore);
        stockRecord.setLockedAfter(lockedAfter);
        stockRecord.setOperatorId(operatorId);
        stockRecord.setRemark(remark);
        stockRecord.setCreateTime(LocalDateTime.now());
        stockRecordMapper.insert(stockRecord);
    }

    private DishStockVO toDishStockVO(DishStock dishStock) {
        return DishStockVO.builder()
                .dishId(dishStock.getDishId())
                .availableStock(dishStock.getAvailableStock())
                .lockedStock(dishStock.getLockedStock())
                .version(dishStock.getVersion())
                .build();
    }
}
