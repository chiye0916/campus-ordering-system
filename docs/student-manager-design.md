# 简化版校园点餐系统设计文档

## 1. 项目目标

本项目用于从任务管理 Demo 过渡到真实业务开发，保留校园点餐系统最核心的业务闭环：

```text
用户浏览商品 -> 加入购物车 -> 提交订单 -> 查询订单 -> 修改订单状态
```

第一版目标不是做完整外卖系统，而是把后端常见业务能力练扎实：

- 用户系统：重新实现注册、登录、JWT、Redis 登录态、拦截器、当前用户上下文。
- 多表设计：分类、商品、购物车、订单、订单明细。
- 一对多关系：一个订单对应多条订单明细。
- 业务链路：Controller -> Service -> Mapper -> MyBatis XML -> MySQL -> Result。
- 事务控制：下单时保证订单、订单明细、购物车清理一起成功或一起失败。
- Redis 缓存：商品列表缓存和缓存删除。
- 登录用户隔离：购物车和订单必须属于当前登录用户。

## 2. 前置假设

- 技术栈沿用已学内容：Java 17、Spring Boot 3、Spring MVC、MyBatis XML、MySQL、Redis、JWT、Validation、Lombok。
- 第一版不引入完整 Spring Security，继续使用 JWT + HandlerInterceptor。
- 当前项目骨架名为 `demo3`，基础包名为 `demo3.demo3_068`，数据库使用 `demo3_db`。
- 继续使用统一返回 `Result<T>`、全局异常处理、DTO/VO/Entity 分层。
- 前端只负责调用接口、保存 token、携带 `Authorization: Bearer <token>`，不负责传当前用户 ID。

## 3. 需求分析

### 3.1 角色和权限

第一版会重新完整实现“登录用户”能力，并预留 `role`：

| 角色 | 能做什么 |
| --- | --- |
| 普通用户 | 浏览商品、管理自己的购物车、提交订单、查看自己的订单、取消自己的订单 |
| 管理员 | 新增/修改分类，新增/修改商品，上下架商品，查看订单，完成订单 |

如果暂时不做管理员登录，可以先把管理接口也放开到登录用户，后续再根据 JWT 中的 `role` 做权限判断。

### 3.2 用户认证规则

| 功能 | 业务规则 |
| --- | --- |
| 注册 | 用户名不能重复；密码必须 BCrypt 加密后保存 |
| 登录 | 根据用户名查询用户；用 BCrypt 校验密码；登录成功后生成 JWT |
| 登录态 | JWT 返回给前端，同时 Redis 保存 `login:user:{userId} -> token` |
| 鉴权 | 拦截器解析 `Authorization: Bearer <token>`，校验 JWT 和 Redis 登录态 |
| 当前用户 | 拦截器把 userId 放入 `BaseContext`；请求结束必须 remove |

### 3.3 核心业务规则

| 模块 | 业务规则 |
| --- | --- |
| 分类 | 分类名称不能重复；删除分类前要判断该分类下是否还有商品 |
| 商品 | 商品价格使用 `BigDecimal`；只有上架商品可被用户加入购物车 |
| 购物车 | 同一用户、同一商品只保留一条购物车记录；重复添加时数量累加 |
| 下单 | 当前用户购物车不能为空；根据购物车计算总金额；生成订单主表和明细；成功后清空购物车 |
| 订单 | 用户只能查看/取消自己的订单；已完成或已取消的订单不能再次取消 |
| 状态 | `1` 待支付，`2` 已支付，`3` 已完成，`4` 已取消 |
| 缓存 | 商品列表可以按分类缓存；商品新增、修改、上下架后删除相关缓存 |

## 4. 技术选型

| 技术 | 用途 | 为什么这样做 | 如果不这样做会怎样 |
| --- | --- | --- | --- |
| Spring MVC | 接收 HTTP 请求 | Controller 清晰表达接口入口 | 接口逻辑容易散在不同地方 |
| MyBatis XML | 编写 SQL | 适合学习 SQL、表关系和动态查询 | 直接用封装框架会弱化 SQL 理解 |
| MySQL | 持久化业务数据 | 订单、明细、购物车需要可靠存储 | 数据无法长期保存 |
| Redis | 缓存商品列表、登录态 | 高频读商品列表，缓存可减少数据库压力 | 每次查询都打 MySQL，扩展性差 |
| JWT + Interceptor | 登录鉴权 | 统一在进入 Controller 前识别用户 | 每个接口重复校验 token |
| ThreadLocal BaseContext | 保存当前用户 ID | Service 不依赖前端传 userId | 前端可伪造 userId 操作他人数据 |
| `@Transactional` | 下单事务 | 保证订单主表、明细、购物车一致 | 可能出现订单不完整或购物车误清空 |
| Validation | 参数校验 | 在 Controller 入参阶段拦截非法数据 | 脏数据进入业务层，异常更难定位 |

## 5. 功能模块

### 5.1 分类模块

请求链路：

```text
前端分类管理页面
  -> CategoryController
  -> CategoryService
  -> CategoryMapper
  -> CategoryMapper.xml
  -> category 表
  -> Result 返回
```

职责：

- Controller：接收新增、修改、删除、查询分类请求。
- Service：处理分类名称重复、删除前是否有关联商品等业务规则。
- Mapper/XML：执行分类表 SQL。
- 数据库：保存分类基础信息。

### 5.2 商品模块

请求链路：

```text
前端商品页面
  -> DishController
  -> DishService
  -> DishMapper / CategoryMapper
  -> DishMapper.xml
  -> dish 表
  -> Redis 商品列表缓存
  -> Result 返回
```

职责：

- Controller：接收商品新增、分页查询、按分类查询、修改、上下架请求。
- Service：校验分类是否存在、处理上下架规则、删除商品缓存。
- Mapper/XML：执行商品 SQL 和分页查询。
- Redis：缓存按分类查询的上架商品列表。

### 5.3 购物车模块

请求链路：

```text
前端购物车页面
  -> CartController
  -> CartService
  -> DishMapper / ShoppingCartMapper
  -> XML
  -> shopping_cart 表
  -> Result 返回
```

职责：

- Controller：接收加入购物车、修改数量、查询、清空请求。
- Service：从 BaseContext 获取当前用户 ID，校验商品是否存在且上架，处理数量累加。
- Mapper/XML：按 `user_id` 操作当前用户购物车。
- 数据库：保存用户购物车临时数据。

### 5.4 订单模块

下单请求链路：

```text
前端点击提交订单
  -> OrderController.submit
  -> OrderService.submitOrder
  -> ShoppingCartMapper 查询当前用户购物车
  -> OrdersMapper 插入订单主表
  -> OrderDetailMapper 批量插入订单明细
  -> ShoppingCartMapper 清空购物车
  -> MySQL 提交事务
  -> Result<Long> 返回订单 ID
```

职责：

- Controller：接收提交订单、查询详情、分页查询、取消、完成请求。
- Service：负责订单状态流转、金额计算、事务控制、用户隔离。
- Mapper/XML：分别操作订单主表和订单明细表。
- 数据库：保存订单快照，避免商品后续改价影响历史订单。

## 6. 数据库表设计

本项目实际使用 6 张表：

```text
user            用户表
category        商品分类表
dish            商品表
shopping_cart   购物车表
orders          订单表
order_detail    订单明细表
```

### 6.1 user 用户表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| username | varchar(64) | 用户名，唯一 |
| password | varchar(255) | BCrypt 加密后的密码 |
| nickname | varchar(64) | 昵称 |
| role | varchar(32) | 角色：USER 或 ADMIN |
| create_time | datetime | 创建时间 |
| update_time | datetime | 更新时间 |

建议约束：

- `username` 建唯一索引，避免重复注册。

### 6.2 category 商品分类表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| name | varchar(64) | 分类名称，唯一 |
| sort | int | 排序 |
| create_time | datetime | 创建时间 |
| update_time | datetime | 更新时间 |

建议约束：

- `name` 建唯一索引，避免重复分类。

### 6.3 dish 商品表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| category_id | bigint | 分类 ID |
| name | varchar(64) | 商品名称 |
| price | decimal(10,2) | 商品价格 |
| image | varchar(255) | 图片地址，第一版可为空 |
| description | varchar(255) | 描述 |
| status | tinyint | 状态：0 下架，1 上架 |
| create_time | datetime | 创建时间 |
| update_time | datetime | 更新时间 |

建议索引：

- `idx_dish_category_id(category_id)`
- `idx_dish_status(status)`

### 6.4 shopping_cart 购物车表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| user_id | bigint | 当前用户 ID |
| dish_id | bigint | 商品 ID |
| dish_name | varchar(64) | 商品名称快照 |
| dish_price | decimal(10,2) | 商品价格快照 |
| quantity | int | 数量 |
| create_time | datetime | 创建时间 |
| update_time | datetime | 更新时间 |

建议约束：

- `uk_cart_user_dish(user_id, dish_id)`，保证同一用户同一商品只有一条记录。

### 6.5 orders 订单表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| number | varchar(64) | 订单号 |
| user_id | bigint | 下单用户 ID |
| status | tinyint | 1 待支付，2 已支付，3 已完成，4 已取消 |
| amount | decimal(10,2) | 订单总金额 |
| remark | varchar(255) | 备注 |
| order_time | datetime | 下单时间 |
| pay_time | datetime | 支付时间，第一版可为空 |
| cancel_time | datetime | 取消时间 |
| complete_time | datetime | 完成时间 |

建议索引：

- `idx_orders_user_id(user_id)`
- `idx_orders_status(status)`
- `uk_orders_number(number)`

### 6.6 order_detail 订单明细表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| order_id | bigint | 订单 ID |
| dish_id | bigint | 商品 ID |
| dish_name | varchar(64) | 商品名称快照 |
| dish_price | decimal(10,2) | 下单时商品单价 |
| quantity | int | 数量 |
| amount | decimal(10,2) | 单项金额 |

建议索引：

- `idx_order_detail_order_id(order_id)`

## 7. REST API 接口设计

### 7.1 用户接口

| 方法 | 路径 | 请求 DTO | 返回 VO | 说明 |
| --- | --- | --- | --- | --- |
| POST | `/user/register` | `UserRegisterDTO` | `Long` | 用户注册 |
| POST | `/user/login` | `UserLoginDTO` | `UserLoginVO` | 用户登录，返回 JWT |
| POST | `/user/logout` | 无 | `Void` | 退出登录，删除 Redis 登录态 |
| GET | `/user/me` | 无 | `UserVO` | 查询当前登录用户 |

登录请求链路：

```text
前端登录页面
  -> UserController.login
  -> UserService.login
  -> UserMapper.selectByUsername
  -> UserMapper.xml
  -> user 表
  -> BCrypt 校验密码
  -> JwtUtil 生成 token
  -> Redis 保存登录态
  -> Result<UserLoginVO> 返回
```

为什么把登录也纳入本项目：

- 后续购物车和订单必须依赖当前用户 ID。
- 如果没有登录系统，用户隔离只能临时写死，真实业务链路不完整。
- 重新实现一遍能把认证、鉴权、ThreadLocal 和业务接口串起来。

### 7.2 分类接口

| 方法 | 路径 | 请求 DTO | 返回 VO | 说明 |
| --- | --- | --- | --- | --- |
| POST | `/category` | `CategoryCreateDTO` | `Long` | 新增分类 |
| GET | `/category/list` | 查询参数 | `List<CategoryVO>` | 查询分类列表 |
| PUT | `/category/{id}` | `CategoryUpdateDTO` | `Void` | 修改分类 |
| DELETE | `/category/{id}` | 路径参数 | `Void` | 删除分类 |

### 7.3 商品接口

| 方法 | 路径 | 请求 DTO | 返回 VO | 说明 |
| --- | --- | --- | --- | --- |
| POST | `/dish` | `DishCreateDTO` | `Long` | 新增商品 |
| GET | `/dish/page` | `DishPageQueryDTO` | `PageResult<DishVO>` | 分页查询商品 |
| GET | `/dish/list` | `DishListQueryDTO` | `List<DishVO>` | 根据分类查询上架商品，可走 Redis |
| PUT | `/dish/{id}` | `DishUpdateDTO` | `Void` | 修改商品 |
| PUT | `/dish/{id}/status` | `DishStatusDTO` | `Void` | 商品上下架 |

### 7.4 购物车接口

| 方法 | 路径 | 请求 DTO | 返回 VO | 说明 |
| --- | --- | --- | --- | --- |
| POST | `/cart/add` | `CartAddDTO` | `Void` | 加入购物车 |
| PUT | `/cart/update` | `CartUpdateDTO` | `Void` | 修改购物车数量 |
| GET | `/cart/list` | 无 | `List<CartVO>` | 查看当前用户购物车 |
| DELETE | `/cart/clean` | 无 | `Void` | 清空当前用户购物车 |

### 7.5 订单接口

| 方法 | 路径 | 请求 DTO | 返回 VO | 说明 |
| --- | --- | --- | --- | --- |
| POST | `/order/submit` | `OrderSubmitDTO` | `Long` | 根据购物车提交订单 |
| GET | `/order/{id}` | 路径参数 | `OrderDetailVO` | 查询订单详情 |
| GET | `/order/page` | `OrderPageQueryDTO` | `PageResult<OrderVO>` | 分页查询订单 |
| PUT | `/order/{id}/cancel` | 路径参数 | `Void` | 取消订单 |
| PUT | `/order/{id}/complete` | 路径参数 | `Void` | 完成订单 |

## 8. DTO / VO / Entity 设计

### 8.1 DTO

| DTO | 关键字段 | 用途 |
| --- | --- | --- |
| `UserRegisterDTO` | `username`, `password`, `nickname` | 用户注册 |
| `UserLoginDTO` | `username`, `password` | 用户登录 |
| `CategoryCreateDTO` | `name`, `sort` | 新增分类 |
| `CategoryUpdateDTO` | `name`, `sort` | 修改分类 |
| `DishCreateDTO` | `categoryId`, `name`, `price`, `image`, `description`, `status` | 新增商品 |
| `DishUpdateDTO` | `categoryId`, `name`, `price`, `image`, `description`, `status` | 修改商品 |
| `DishPageQueryDTO` | `page`, `pageSize`, `name`, `categoryId`, `status` | 商品分页 |
| `DishListQueryDTO` | `categoryId` | 按分类查询商品 |
| `DishStatusDTO` | `status` | 上下架 |
| `CartAddDTO` | `dishId`, `quantity` | 加入购物车 |
| `CartUpdateDTO` | `dishId`, `quantity` | 修改数量 |
| `OrderSubmitDTO` | `remark` | 提交订单 |
| `OrderPageQueryDTO` | `page`, `pageSize`, `status` | 订单分页 |

### 8.2 VO

| VO | 关键字段 | 用途 |
| --- | --- | --- |
| `UserVO` | `id`, `username`, `nickname`, `role` | 当前用户信息 |
| `UserLoginVO` | `id`, `username`, `nickname`, `role`, `token` | 登录成功返回 |
| `CategoryVO` | `id`, `name`, `sort` | 分类列表 |
| `DishVO` | `id`, `categoryId`, `categoryName`, `name`, `price`, `image`, `description`, `status` | 商品展示 |
| `CartVO` | `dishId`, `dishName`, `dishPrice`, `quantity`, `amount` | 购物车展示 |
| `OrderVO` | `id`, `number`, `status`, `amount`, `orderTime` | 订单列表 |
| `OrderDetailVO` | 订单主信息 + `List<OrderItemVO>` | 订单详情 |
| `OrderItemVO` | `dishId`, `dishName`, `dishPrice`, `quantity`, `amount` | 订单明细 |
| `PageResult<T>` | `total`, `records` | 分页返回 |

### 8.3 Entity

Entity 与数据库表一一对应：

- `User`
- `Category`
- `Dish`
- `ShoppingCart`
- `Orders`
- `OrderDetail`

为什么区分 DTO、VO、Entity：

- DTO 只表达“前端允许传什么”。
- VO 只表达“后端允许返回什么”。
- Entity 只表达“数据库表长什么样”。
- 如果混用，前端可能传入不该传的字段，例如 `userId`、`status`、`amount`，造成安全和数据一致性问题。

## 9. 项目目录结构

建议目录：

```text
src/main/java/demo3/demo3_068
  common
    Result.java
    PageResult.java
    BaseContext.java
  config
    WebMvcConfig.java
    RedisConfig.java
  controller
    UserController.java
    CategoryController.java
    DishController.java
    CartController.java
    OrderController.java
  dto
    UserRegisterDTO.java
    UserLoginDTO.java
    CategoryCreateDTO.java
    CategoryUpdateDTO.java
    DishCreateDTO.java
    DishUpdateDTO.java
    DishPageQueryDTO.java
    DishListQueryDTO.java
    DishStatusDTO.java
    CartAddDTO.java
    CartUpdateDTO.java
    OrderSubmitDTO.java
    OrderPageQueryDTO.java
  entity
    User.java
    Category.java
    Dish.java
    ShoppingCart.java
    Orders.java
    OrderDetail.java
  exception
    BusinessException.java
    GlobalExceptionHandler.java
  interceptor
    JwtTokenInterceptor.java
  mapper
    UserMapper.java
    CategoryMapper.java
    DishMapper.java
    ShoppingCartMapper.java
    OrdersMapper.java
    OrderDetailMapper.java
  service
    UserService.java
    CategoryService.java
    DishService.java
    CartService.java
    OrderService.java
  service/impl
    UserServiceImpl.java
    CategoryServiceImpl.java
    DishServiceImpl.java
    CartServiceImpl.java
    OrderServiceImpl.java
  utils
    JwtUtil.java
    OrderNumberUtil.java

src/main/resources
  mapper
    UserMapper.xml
    CategoryMapper.xml
    DishMapper.xml
    ShoppingCartMapper.xml
    OrdersMapper.xml
    OrderDetailMapper.xml

sql
  schema.sql
```

当前仓库真实基础包名是 `demo3.demo3_068`，实现时应使用：

```text
src/main/java/demo3/demo3_068
```

## 10. 统一返回和异常设计

统一返回：

```java
Result.success(data)
Result.success()
Result.error(code, message)
```

建议错误码：

| 错误码 | 场景 |
| --- | --- |
| 400 | 参数错误 |
| 401 | 未登录或 token 无效 |
| 403 | 无权限 |
| 404 | 数据不存在 |
| 409 | 业务冲突，例如分类重复、订单状态不允许修改 |
| 500 | 系统异常 |

异常处理：

- `BusinessException`：业务规则失败，例如购物车为空、商品已下架。
- `GlobalExceptionHandler`：统一捕获业务异常、参数校验异常、系统异常。

为什么要统一：

- Controller 不需要到处写 `try/catch`。
- 前端能按统一格式处理成功和失败。
- 业务错误和系统错误分开，便于定位问题。

## 11. 参数校验设计

示例规则：

| 字段 | 校验 |
| --- | --- |
| 分类名称 | `@NotBlank` |
| 排序 | `@NotNull`, `@Min(0)` |
| 商品价格 | `@NotNull`, `@DecimalMin("0.01")` |
| 商品状态 | 只能是 `0` 或 `1` |
| 购物车数量 | `@NotNull`, `@Min(1)` |
| 分页页码 | `@Min(1)` |
| 分页大小 | `@Min(1)`, `@Max(100)` |

为什么做参数校验：

- 非法请求在进入业务逻辑前就被拦截。
- Service 可以专注处理业务规则。
- 如果不校验，可能出现负数价格、数量为 0、空分类名等脏数据。

## 12. Redis 缓存设计

建议缓存 key：

```text
dish:list:category:{categoryId}
```

查询商品列表流程：

```text
GET /dish/list?categoryId=1
  -> DishController
  -> DishService
  -> 先查 Redis key dish:list:category:1
  -> 命中：直接返回 List<DishVO>
  -> 未命中：查 MySQL 上架商品
  -> 写入 Redis
  -> 返回 Result<List<DishVO>>
```

缓存删除时机：

- 新增商品后删除该分类缓存。
- 修改商品后删除旧分类和新分类缓存。
- 商品上下架后删除该分类缓存。

为什么删除缓存而不是直接改缓存：

- 第一版实现更简单，不容易出现缓存和数据库字段不一致。
- 下一次查询会从数据库重新加载最新数据。

如果不处理缓存删除：

- 商品已下架，但用户仍可能从缓存里看到并加入购物车。
- 商品改价后，列表显示旧价格。

## 13. 事务控制设计

下单方法必须加 `@Transactional`：

```java
@Transactional
public Long submitOrder(OrderSubmitDTO orderSubmitDTO) {
    // 1. 查询当前用户购物车
    // 2. 判断购物车是否为空
    // 3. 计算订单总金额
    // 4. 插入 orders
    // 5. 批量插入 order_detail
    // 6. 清空当前用户购物车
    // 7. 返回订单 ID
}
```

为什么需要事务：

- 订单主表和明细表是一组业务数据，必须同时成功。
- 清空购物车必须发生在订单保存成功之后。
- 如果没有事务，可能出现“订单主表插入成功、明细失败、购物车被清空”的数据一致性问题。

金额计算：

- 使用 `BigDecimal`，不要用 `double`。
- 单项金额：`dishPrice.multiply(BigDecimal.valueOf(quantity))`。
- 总金额：所有单项金额累加。

为什么不用 `double`：

- `double` 是二进制浮点数，可能出现精度误差。
- 金额属于严肃业务字段，必须用精确小数。

## 14. 状态机设计

订单状态：

```text
1 待支付
2 已支付
3 已完成
4 已取消
```

第一版建议状态流转：

```text
待支付 -> 已取消
待支付 -> 已支付
已支付 -> 已完成
```

接口对应：

- `/order/{id}/cancel`：待支付或已支付订单可取消，已完成不可取消。
- `/order/{id}/complete`：已支付订单可完成，待支付不可直接完成。

为什么要按状态机处理：

- 防止订单从“已取消”又变成“已完成”。
- 防止重复取消、重复完成。
- 复杂业务其实就是状态和规则的组合。

## 15. 开发阶段拆分

### 阶段 0：项目初始化和基础结构

内容：

- 创建包结构。
- 添加 `Result`、`PageResult`、异常处理、基础配置。
- 准备 `schema.sql`。
- 配置 MyBatis XML 路径。

验收：

- 项目能启动。
- `./mvnw test` 能通过。

### 阶段 1：用户注册登录模块

内容：

- 实现用户注册、登录、退出登录、查询当前用户。
- 实现 BCrypt 密码加密。
- 实现 JWT 生成和解析。
- 实现 Redis 登录态。
- 实现拦截器和 `BaseContext`。

验收：

- 注册成功后 `user` 表保存加密密码。
- 登录成功后返回 token，Redis 保存登录态。
- 未登录请求受保护接口会返回 401。
- 请求结束后清理 `ThreadLocal`。

### 阶段 2：分类模块

内容：

- 实现分类新增、查询、修改、删除。
- 删除分类前检查是否有关联商品。

验收：

- 能新增分类。
- 重复名称会报业务异常。
- 分类列表能按 sort 排序返回。

### 阶段 3：商品模块

内容：

- 实现新增商品、分页查询、按分类查询、修改、上下架。
- 商品列表接入 Redis 缓存。
- 商品变更后删除缓存。

验收：

- 上架商品能在 `/dish/list` 查到。
- 下架商品不能加入购物车。
- 修改或上下架后商品列表缓存会更新。

### 阶段 4：购物车模块

内容：

- 实现加入购物车、修改数量、查看、清空。
- 所有购物车操作都按当前登录用户隔离。

验收：

- 同一商品重复添加时数量累加。
- 当前用户只能看到自己的购物车。
- 数量改为合法值后金额展示正确。

### 阶段 5：订单模块

内容：

- 实现提交订单。
- 实现订单详情、订单分页、取消订单、完成订单。
- 下单方法加 `@Transactional`。

验收：

- 购物车为空时不能下单。
- 下单后生成订单主表和明细表。
- 下单成功后购物车清空。
- 非法状态流转会报业务异常。

### 阶段 6：复盘和接口测试

内容：

- 整理接口测试文档。
- 复盘完整请求链路。
- 整理常见异常和排错方式。

验收：

- 能从“用户提交订单”完整讲出 Controller、Service、Mapper/XML、数据库变化和返回值。

## 16. 测试方式

### 16.1 命令测试

```bash
./mvnw test
```

### 16.2 接口测试

建议使用 Postman、Apifox 或浏览器 fetch。

基本顺序：

```text
1. 登录，拿到 token
2. 新增分类
3. 新增商品
4. 查询商品列表
5. 加入购物车
6. 查询购物车
7. 提交订单
8. 查询订单详情
9. 修改订单状态
```

### 16.3 数据库验证

下单后检查：

- `orders` 是否新增 1 条记录。
- `order_detail` 是否新增多条明细。
- `shopping_cart` 当前用户数据是否清空。
- `orders.amount` 是否等于明细金额合计。

## 17. 初学者学习重点

- 为什么 Controller 不直接调用 Mapper：避免接口层混入业务规则，后续难维护。
- 为什么 Service 要负责业务逻辑：业务判断、事务、缓存删除、状态流转都属于业务层。
- 为什么当前用户 ID 不能让前端传：前端参数可伪造，必须以后端解析 JWT 为准。
- 为什么下单要保存商品快照：商品后续改名或改价，不应该影响历史订单。
- 为什么订单主表和订单明细要分开：一个订单可以包含多个商品，这是典型一对多关系。
- 为什么需要 Redis 缓存：商品列表读多写少，适合缓存优化。
- 为什么修改商品后要删除缓存：避免用户看到旧商品数据。
- 为什么状态要有规则：订单状态不是随便改的，必须符合业务流程。

## 18. 下一步建议

第 1 阶段从“项目初始化和基础结构”开始：

```text
先建 common / exception / config / mapper XML 基础目录
再建 schema.sql
再实现用户注册登录，作为后续购物车和订单用户隔离的基础
```

每完成一个阶段，都按下面方式复盘：

```text
请求从哪里来
进入哪个 Controller
调用哪个 Service
查哪个 Mapper/XML
数据库怎么变
返回什么 Result
为什么这样设计
如果不这样做会怎样
```
