package demo3.demo3_068.service.impl;

import demo3.demo3_068.common.BaseContext;
import demo3.demo3_068.common.Constants;
import demo3.demo3_068.common.PageResult;
import demo3.demo3_068.dto.OrderPageQueryDTO;
import demo3.demo3_068.dto.OrderSubmitDTO;
import demo3.demo3_068.entity.Dish;
import demo3.demo3_068.entity.OrderDetail;
import demo3.demo3_068.entity.Orders;
import demo3.demo3_068.entity.ShoppingCart;
import demo3.demo3_068.mapper.DishMapper;
import demo3.demo3_068.exception.BusinessException;
import demo3.demo3_068.mapper.OrderDetailMapper;
import demo3.demo3_068.mapper.OrdersMapper;
import demo3.demo3_068.mapper.ShoppingCartMapper;
import demo3.demo3_068.service.OrderService;
import demo3.demo3_068.utils.OrderNumberUtil;
import demo3.demo3_068.vo.OrderDetailVO;
import demo3.demo3_068.vo.OrderItemVO;
import demo3.demo3_068.vo.OrderPayVO;
import demo3.demo3_068.vo.OrderVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    private static final int STATUS_PENDING_PAYMENT = 1;
    private static final int STATUS_PAID = 2;
    private static final int STATUS_COMPLETED = 3;
    private static final int STATUS_CANCELLED = 4;

    private final OrdersMapper ordersMapper;
    private final OrderDetailMapper orderDetailMapper;
    private final ShoppingCartMapper shoppingCartMapper;
    private final DishMapper dishMapper;

    public OrderServiceImpl(OrdersMapper ordersMapper,
                            OrderDetailMapper orderDetailMapper,
                            ShoppingCartMapper shoppingCartMapper,
                            DishMapper dishMapper) {
        this.ordersMapper = ordersMapper;
        this.orderDetailMapper = orderDetailMapper;
        this.shoppingCartMapper = shoppingCartMapper;
        this.dishMapper = dishMapper;
    }

    @Override
    @Transactional
    public Long submit(OrderSubmitDTO orderSubmitDTO) {
        Long userId = getCurrentUserIdOrThrow();
        List<ShoppingCart> cartItems = shoppingCartMapper.selectByUserId(userId);
        if (cartItems.isEmpty()) {
            throw new BusinessException("购物车为空，不能下单");
        }
        List<ShoppingCart> refreshedCartItems = refreshCartItems(cartItems);

        BigDecimal totalAmount = refreshedCartItems.stream()
                .map(item -> item.getDishPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Orders orders = new Orders();
        orders.setNumber(OrderNumberUtil.generateOrderNumber());
        orders.setUserId(userId);
        orders.setStatus(STATUS_PENDING_PAYMENT);
        orders.setAmount(totalAmount);
        orders.setRemark(orderSubmitDTO.getRemark());
        orders.setOrderTime(LocalDateTime.now());
        ordersMapper.insert(orders);

        List<OrderDetail> details = refreshedCartItems.stream()
                .map(item -> toOrderDetail(orders.getId(), item))
                .toList();
        orderDetailMapper.insertBatch(details);

        shoppingCartMapper.deleteByUserId(userId);
        return orders.getId();
    }

    @Override
    public OrderDetailVO getDetail(Long id) {
        Orders orders = getVisibleOrderOrThrow(id);
        List<OrderItemVO> items = orderDetailMapper.selectByOrderId(id).stream()
                .map(this::toOrderItemVO)
                .toList();
        return toOrderDetailVO(orders, items);
    }

    @Override
    public PageResult<OrderVO> page(OrderPageQueryDTO orderPageQueryDTO) {
        Long userId = getQueryUserId();
        int offset = (orderPageQueryDTO.getPage() - 1) * orderPageQueryDTO.getPageSize();
        long total = ordersMapper.countPage(userId, orderPageQueryDTO);
        List<OrderVO> records = ordersMapper.selectPage(userId, orderPageQueryDTO, offset, orderPageQueryDTO.getPageSize())
                .stream()
                .map(this::toOrderVO)
                .toList();
        return new PageResult<>(total, records);
    }

    @Override
    public OrderPayVO pay(Long id) {
        Orders orders = getOwnOrderOrThrow(id);
        if (orders.getStatus() != STATUS_PENDING_PAYMENT) {
            throw new BusinessException("只有待支付订单才能支付");
        }

        LocalDateTime payTime = LocalDateTime.now();
        int rows = ordersMapper.updateToPaidById(id, payTime, STATUS_PENDING_PAYMENT, STATUS_PAID);
        if (rows == 0) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }

        return OrderPayVO.builder()
                .orderId(orders.getId())
                .orderNumber(orders.getNumber())
                .status(STATUS_PAID)
                .amount(orders.getAmount())
                .payTime(payTime)
                .build();
    }

    @Override
    public void cancel(Long id) {
        Orders orders = getOwnOrderOrThrow(id);
        if (orders.getStatus() == STATUS_COMPLETED) {
            throw new BusinessException("已完成订单不能取消");
        }
        if (orders.getStatus() == STATUS_CANCELLED) {
            throw new BusinessException("订单已取消，不能重复取消");
        }

        ordersMapper.updateToCancelledById(id, LocalDateTime.now(), STATUS_CANCELLED);
    }

    @Override
    public void complete(Long id) {
        Orders orders = getOrderOrThrow(id);
        if (orders.getStatus() != STATUS_PAID) {
            throw new BusinessException("只有已支付订单才能完成");
        }

        int rows = ordersMapper.updateToCompletedById(id, LocalDateTime.now(), STATUS_PAID, STATUS_COMPLETED);
        if (rows == 0) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }
    }

    private Orders getOwnOrderOrThrow(Long id) {
        Long userId = getCurrentUserIdOrThrow();
        Orders orders = ordersMapper.selectById(id);
        if (orders == null || !userId.equals(orders.getUserId())) {
            throw new BusinessException(404, "订单不存在");
        }
        return orders;
    }

    private Orders getVisibleOrderOrThrow(Long id) {
        Orders orders = getOrderOrThrow(id);
        if (isAdmin()) {
            return orders;
        }
        Long userId = getCurrentUserIdOrThrow();
        if (!userId.equals(orders.getUserId())) {
            throw new BusinessException(404, "订单不存在");
        }
        return orders;
    }

    private Orders getOrderOrThrow(Long id) {
        Orders orders = ordersMapper.selectById(id);
        if (orders == null) {
            throw new BusinessException(404, "订单不存在");
        }
        return orders;
    }

    private Long getQueryUserId() {
        Long userId = getCurrentUserIdOrThrow();
        return isAdmin() ? null : userId;
    }

    private boolean isAdmin() {
        return Constants.ADMIN_USER_ROLE.equals(BaseContext.getCurrentUserRole());
    }

    private Long getCurrentUserIdOrThrow() {
        Long userId = BaseContext.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        return userId;
    }

    private List<ShoppingCart> refreshCartItems(List<ShoppingCart> cartItems) {
        return cartItems.stream()
                .map(this::refreshCartItem)
                .toList();
    }

    private ShoppingCart refreshCartItem(ShoppingCart cartItem) {
        Dish dish = dishMapper.selectById(cartItem.getDishId());
        if (dish == null) {
            throw new BusinessException("购物车中存在已删除商品，请先移出购物车");
        }
        if (!Integer.valueOf(1).equals(dish.getStatus())) {
            throw new BusinessException("购物车中存在已下架商品，请先移出购物车");
        }
        cartItem.setDishName(dish.getName());
        cartItem.setDishPrice(dish.getPrice());
        return cartItem;
    }

    private OrderDetail toOrderDetail(Long orderId, ShoppingCart shoppingCart) {
        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setOrderId(orderId);
        orderDetail.setDishId(shoppingCart.getDishId());
        orderDetail.setDishName(shoppingCart.getDishName());
        orderDetail.setDishPrice(shoppingCart.getDishPrice());
        orderDetail.setQuantity(shoppingCart.getQuantity());
        orderDetail.setAmount(shoppingCart.getDishPrice().multiply(BigDecimal.valueOf(shoppingCart.getQuantity())));
        return orderDetail;
    }

    private OrderItemVO toOrderItemVO(OrderDetail orderDetail) {
        return OrderItemVO.builder()
                .dishId(orderDetail.getDishId())
                .dishName(orderDetail.getDishName())
                .dishPrice(orderDetail.getDishPrice())
                .quantity(orderDetail.getQuantity())
                .amount(orderDetail.getAmount())
                .build();
    }

    private OrderDetailVO toOrderDetailVO(Orders orders, List<OrderItemVO> items) {
        return OrderDetailVO.builder()
                .id(orders.getId())
                .number(orders.getNumber())
                .status(orders.getStatus())
                .amount(orders.getAmount())
                .remark(orders.getRemark())
                .orderTime(orders.getOrderTime())
                .payTime(orders.getPayTime())
                .cancelTime(orders.getCancelTime())
                .completeTime(orders.getCompleteTime())
                .items(items)
                .build();
    }

    private OrderVO toOrderVO(Orders orders) {
        return OrderVO.builder()
                .id(orders.getId())
                .number(orders.getNumber())
                .status(orders.getStatus())
                .amount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();
    }
}
