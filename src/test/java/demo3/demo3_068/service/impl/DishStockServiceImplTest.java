package demo3.demo3_068.service.impl;

import demo3.demo3_068.common.BaseContext;
import demo3.demo3_068.dto.DishStockSetDTO;
import demo3.demo3_068.entity.Dish;
import demo3.demo3_068.entity.DishStock;
import demo3.demo3_068.entity.StockRecord;
import demo3.demo3_068.exception.BusinessException;
import demo3.demo3_068.mapper.DishMapper;
import demo3.demo3_068.mapper.DishStockMapper;
import demo3.demo3_068.mapper.StockRecordMapper;
import demo3.demo3_068.model.StockChangeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DishStockServiceImplTest {

    @Mock
    private DishStockMapper dishStockMapper;
    @Mock
    private StockRecordMapper stockRecordMapper;
    @Mock
    private DishMapper dishMapper;

    private DishStockServiceImpl dishStockService;

    @BeforeEach
    void setUp() {
        dishStockService = new DishStockServiceImpl(dishStockMapper, stockRecordMapper, dishMapper);
        BaseContext.setCurrentUserId(9L);
        BaseContext.setCurrentUserRole("ADMIN");
    }

    @AfterEach
    void tearDown() {
        BaseContext.clear();
    }

    @Test
    void setStockCreatesMissingRowAndWritesSetRecord() {
        when(dishMapper.selectById(1L)).thenReturn(dish(1L));
        when(dishStockMapper.selectByDishIdForUpdate(1L)).thenReturn(null, stock(1L, 0, 0));
        when(dishStockMapper.insert(any(DishStock.class))).thenReturn(1);
        when(dishStockMapper.updateAvailableStockByDishId(1L, 10)).thenReturn(1);

        dishStockService.setStock(1L, setDTO(10, "初始库存"));

        verify(dishStockMapper).insert(any(DishStock.class));
        ArgumentCaptor<StockRecord> captor = ArgumentCaptor.forClass(StockRecord.class);
        verify(stockRecordMapper).insert(captor.capture());
        StockRecord record = captor.getValue();
        assertThat(record.getChangeType()).isEqualTo(StockChangeType.SET.name());
        assertThat(record.getChangeQuantity()).isEqualTo(10);
        assertThat(record.getAvailableBefore()).isZero();
        assertThat(record.getAvailableAfter()).isEqualTo(10);
        assertThat(record.getLockedBefore()).isZero();
        assertThat(record.getLockedAfter()).isZero();
        assertThat(record.getOperatorId()).isEqualTo(9L);
    }

    @Test
    void setStockKeepsLockedStockUnchanged() {
        when(dishMapper.selectById(1L)).thenReturn(dish(1L));
        when(dishStockMapper.selectByDishIdForUpdate(1L)).thenReturn(stock(1L, 5, 3));
        when(dishStockMapper.updateAvailableStockByDishId(1L, 8)).thenReturn(1);

        dishStockService.setStock(1L, setDTO(8, "补货"));

        ArgumentCaptor<StockRecord> captor = ArgumentCaptor.forClass(StockRecord.class);
        verify(stockRecordMapper).insert(captor.capture());
        assertThat(captor.getValue().getChangeQuantity()).isEqualTo(3);
        assertThat(captor.getValue().getLockedBefore()).isEqualTo(3);
        assertThat(captor.getValue().getLockedAfter()).isEqualTo(3);
    }

    @Test
    void lockStockUpdatesAvailableAndLockedAndWritesRecord() {
        when(dishStockMapper.selectByDishIdForUpdate(1L)).thenReturn(stock(1L, 10, 1));
        when(dishStockMapper.lockStock(1L, 2)).thenReturn(1);

        dishStockService.lockStock(101L, Map.of(1L, 2), 7L);

        ArgumentCaptor<StockRecord> captor = ArgumentCaptor.forClass(StockRecord.class);
        verify(stockRecordMapper).insert(captor.capture());
        StockRecord record = captor.getValue();
        assertThat(record.getChangeType()).isEqualTo(StockChangeType.LOCK.name());
        assertThat(record.getOrderId()).isEqualTo(101L);
        assertThat(record.getChangeQuantity()).isEqualTo(2);
        assertThat(record.getAvailableBefore()).isEqualTo(10);
        assertThat(record.getAvailableAfter()).isEqualTo(8);
        assertThat(record.getLockedBefore()).isEqualTo(1);
        assertThat(record.getLockedAfter()).isEqualTo(3);
        assertThat(record.getOperatorId()).isEqualTo(7L);
    }

    @Test
    void confirmLockedStockWritesConfirmRecord() {
        when(dishStockMapper.selectByDishIdForUpdate(1L)).thenReturn(stock(1L, 8, 3));
        when(dishStockMapper.confirmLockedStock(1L, 3)).thenReturn(1);

        dishStockService.confirmLockedStock(101L, Map.of(1L, 3), 7L);

        ArgumentCaptor<StockRecord> captor = ArgumentCaptor.forClass(StockRecord.class);
        verify(stockRecordMapper).insert(captor.capture());
        assertThat(captor.getValue().getChangeType()).isEqualTo(StockChangeType.CONFIRM.name());
        assertThat(captor.getValue().getAvailableAfter()).isEqualTo(8);
        assertThat(captor.getValue().getLockedAfter()).isZero();
    }

    @Test
    void releaseLockedStockWritesReleaseRecord() {
        when(dishStockMapper.selectByDishIdForUpdate(1L)).thenReturn(stock(1L, 8, 3));
        when(dishStockMapper.releaseLockedStock(1L, 3)).thenReturn(1);

        dishStockService.releaseLockedStock(101L, Map.of(1L, 3), 7L);

        ArgumentCaptor<StockRecord> captor = ArgumentCaptor.forClass(StockRecord.class);
        verify(stockRecordMapper).insert(captor.capture());
        assertThat(captor.getValue().getChangeType()).isEqualTo(StockChangeType.RELEASE.name());
        assertThat(captor.getValue().getAvailableAfter()).isEqualTo(11);
        assertThat(captor.getValue().getLockedAfter()).isZero();
    }

    @Test
    void missingStockRowPreventsOrderStockOperation() {
        when(dishStockMapper.selectByDishIdForUpdate(1L)).thenReturn(null);

        assertThatThrownBy(() -> dishStockService.lockStock(101L, Map.of(1L, 1), 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("商品库存未初始化");
        verify(stockRecordMapper, never()).insert(any());
    }

    @Test
    void multiDishLockFailureThrowsSoTransactionCanRollbackPreviousLocks() {
        when(dishStockMapper.selectByDishIdForUpdate(1L)).thenReturn(stock(1L, 5, 0));
        when(dishStockMapper.lockStock(1L, 2)).thenReturn(1);
        when(dishStockMapper.selectByDishIdForUpdate(2L)).thenReturn(stock(2L, 1, 0));
        when(dishStockMapper.lockStock(2L, 3)).thenReturn(0);

        assertThatThrownBy(() -> dishStockService.lockStock(101L, Map.of(2L, 3, 1L, 2), 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("商品库存不足");
    }

    private DishStockSetDTO setDTO(Integer availableStock, String remark) {
        DishStockSetDTO dto = new DishStockSetDTO();
        dto.setAvailableStock(availableStock);
        dto.setRemark(remark);
        return dto;
    }

    private DishStock stock(Long dishId, int availableStock, int lockedStock) {
        DishStock dishStock = new DishStock();
        dishStock.setDishId(dishId);
        dishStock.setAvailableStock(availableStock);
        dishStock.setLockedStock(lockedStock);
        dishStock.setVersion(0);
        return dishStock;
    }

    private Dish dish(Long id) {
        Dish dish = new Dish();
        dish.setId(id);
        return dish;
    }
}
