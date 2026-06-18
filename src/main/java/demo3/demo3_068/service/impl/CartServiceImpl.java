package demo3.demo3_068.service.impl;

import demo3.demo3_068.common.BaseContext;
import demo3.demo3_068.dto.CartAddDTO;
import demo3.demo3_068.dto.CartUpdateDTO;
import demo3.demo3_068.entity.Dish;
import demo3.demo3_068.entity.ShoppingCart;
import demo3.demo3_068.exception.BusinessException;
import demo3.demo3_068.mapper.DishMapper;
import demo3.demo3_068.mapper.ShoppingCartMapper;
import demo3.demo3_068.service.CartService;
import demo3.demo3_068.vo.CartVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CartServiceImpl implements CartService {

    private final ShoppingCartMapper shoppingCartMapper;
    private final DishMapper dishMapper;

    public CartServiceImpl(ShoppingCartMapper shoppingCartMapper, DishMapper dishMapper) {
        this.shoppingCartMapper = shoppingCartMapper;
        this.dishMapper = dishMapper;
    }

    @Override
    public void add(CartAddDTO cartAddDTO) {
        Long userId = getCurrentUserIdOrThrow();
        Dish dish = getAvailableDishOrThrow(cartAddDTO.getDishId());

        ShoppingCart existingCart = shoppingCartMapper.selectByUserIdAndDishId(userId, dish.getId());
        if (existingCart != null) {
            int newQuantity = existingCart.getQuantity() + cartAddDTO.getQuantity();
            shoppingCartMapper.updateItemById(existingCart.getId(), dish.getName(), dish.getPrice(), newQuantity);
            return;
        }

        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        shoppingCart.setDishId(dish.getId());
        shoppingCart.setDishName(dish.getName());
        shoppingCart.setDishPrice(dish.getPrice());
        shoppingCart.setQuantity(cartAddDTO.getQuantity());
        shoppingCartMapper.insert(shoppingCart);
    }

    @Override
    public void update(CartUpdateDTO cartUpdateDTO) {
        Long userId = getCurrentUserIdOrThrow();
        ShoppingCart shoppingCart = shoppingCartMapper.selectByUserIdAndDishId(userId, cartUpdateDTO.getDishId());
        if (shoppingCart == null) {
            throw new BusinessException(404, "购物车中不存在该商品");
        }
        shoppingCartMapper.updateQuantityById(shoppingCart.getId(), cartUpdateDTO.getQuantity());
    }

    @Override
    public List<CartVO> list() {
        Long userId = getCurrentUserIdOrThrow();
        return shoppingCartMapper.selectByUserId(userId).stream()
                .map(this::toCartVO)
                .toList();
    }

    @Override
    public void deleteByDishId(Long dishId) {
        Long userId = getCurrentUserIdOrThrow();
        ShoppingCart shoppingCart = shoppingCartMapper.selectByUserIdAndDishId(userId, dishId);
        if (shoppingCart == null) {
            throw new BusinessException(404, "购物车中不存在该商品");
        }
        shoppingCartMapper.deleteByUserIdAndDishId(userId, dishId);
    }

    @Override
    public void clean() {
        Long userId = getCurrentUserIdOrThrow();
        shoppingCartMapper.deleteByUserId(userId);
    }

    private Dish getAvailableDishOrThrow(Long dishId) {
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null) {
            throw new BusinessException(404, "商品不存在");
        }
        if (!Integer.valueOf(1).equals(dish.getStatus())) {
            throw new BusinessException("商品已下架，不能加入购物车");
        }
        return dish;
    }

    private Long getCurrentUserIdOrThrow() {
        Long userId = BaseContext.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        return userId;
    }

    private CartVO toCartVO(ShoppingCart shoppingCart) {
        Dish dish = dishMapper.selectById(shoppingCart.getDishId());
        if (dish == null) {
            BigDecimal amount = shoppingCart.getDishPrice().multiply(BigDecimal.valueOf(shoppingCart.getQuantity()));
            return CartVO.builder()
                    .dishId(shoppingCart.getDishId())
                    .dishName(shoppingCart.getDishName())
                    .dishPrice(shoppingCart.getDishPrice())
                    .quantity(shoppingCart.getQuantity())
                    .amount(amount)
                    .dishStatus(0)
                    .available(false)
                    .changeMessage("商品已删除，请移出购物车")
                    .build();
        }

        boolean available = Integer.valueOf(1).equals(dish.getStatus());
        BigDecimal amount = dish.getPrice().multiply(BigDecimal.valueOf(shoppingCart.getQuantity()));
        String changeMessage = null;
        if (!available) {
            changeMessage = "商品已下架，请移出购物车";
        } else if (dish.getPrice().compareTo(shoppingCart.getDishPrice()) != 0) {
            changeMessage = "价格已更新";
        } else if (!dish.getName().equals(shoppingCart.getDishName())) {
            changeMessage = "商品信息已更新";
        }
        return CartVO.builder()
                .dishId(shoppingCart.getDishId())
                .dishName(dish.getName())
                .dishPrice(dish.getPrice())
                .quantity(shoppingCart.getQuantity())
                .amount(amount)
                .dishStatus(dish.getStatus())
                .available(available)
                .changeMessage(changeMessage)
                .build();
    }
}
