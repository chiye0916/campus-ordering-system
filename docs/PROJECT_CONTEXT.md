# Project Context

新会话开始时，先阅读本文件恢复项目上下文。

## 项目名称

简化版校园点餐系统

## 项目目标

实现一个用于学习 Java/Spring Boot 后端业务开发的校园点餐 Demo，核心闭环是：

```text
用户浏览商品 -> 加入购物车 -> 提交订单 -> 查询订单 -> 修改订单状态
```

完整设计文档见：[student-manager-design.md](student-manager-design.md)。

## 技术栈

- Java 17
- Spring Boot 3.5.14
- Spring MVC
- MyBatis XML，不使用 MyBatis-Plus
- MySQL
- Redis
- JWT + HandlerInterceptor，不先引入完整 Spring Security
- Lombok
- Validation
- Maven Wrapper

## 当前状态

已完成：

- 已创建项目设计文档：`docs/student-manager-design.md`
- 当前仓库是 Spring Boot 基础骨架
- `pom.xml` 已包含 Web、Validation、Redis、MyBatis、MySQL、Lombok、测试依赖
- 用户已创建 MySQL 数据库 `demo3_db`，并授权 `chiye` 用户访问
- 已确认本项目要重新完整实现用户注册/登录/JWT，不从旧项目复制认证模块
- 已创建 6 张表：`user`、`category`、`dish`、`shopping_cart`、`orders`、`order_detail`
- 已实现用户注册、登录、退出登录、查询当前用户的基础代码
- 已实现 JWT 工具、Redis 登录态、JWT 拦截器、`BaseContext` 当前用户上下文
- 已完成用户模块 HTTP 联调：注册成功、登录成功、带 token 查询当前用户成功、退出后旧 token 返回 401
- 已实现分类模块基础 CRUD：新增分类、查询分类列表、修改分类、删除分类
- 删除分类时已预留商品引用检查：`DishMapper.countByCategoryId`
- 已实现商品模块基础接口：新增商品、分页查询、按分类查询、修改商品、上下架商品
- `/dish/list` 已接入 Redis 缓存，商品新增/修改/上下架后会删除对应分类商品列表缓存
- 已实现购物车模块：加入购物车、修改数量、查看购物车、清空购物车
- 购物车使用 `BaseContext` 获取当前用户 ID，前端不传 `userId`
- 已实现订单模块：提交订单、订单详情、订单分页、支付、取消、接单、开始配送、完成订单、管理员内部模拟退款
- 下单使用 `@Transactional`，保证订单主表、订单明细、购物车清空同时成功或回滚
- `POST /order/submit` 已加入 MySQL 下单幂等：客户端必须传 `Idempotency-Key`，成功仍返回 `Result<Long>` 的订单 ID
- 已新增 `order_idempotency` 表，用 `(user_id, idempotency_key)` 唯一约束记录下单幂等状态
- 已补充订单支付接口：`PUT /order/{id}/pay`
- 已新增库存模块：`dish_stock` 保存可用/锁定库存，`stock_record` 保存 SET/LOCK/CONFIRM/RELEASE 库存流水
- 已新增管理员库存接口：`GET /dish/{id}/stock`、`PUT /dish/{id}/stock`，设置字段为 `availableStock`
- 下单会先创建待支付订单获得 `order_id`，再按 dishId 聚合并升序锁库存、写 `LOCK` 流水；幂等重复返回原订单时不会重复锁库存
- 支付待支付订单会在订单状态条件更新成功后确认锁定库存、写 `CONFIRM` 流水；重复支付不会重复扣锁定库存
- 取消待支付订单会在订单状态条件更新成功后释放锁定库存、写 `RELEASE` 流水；重复取消不会重复释放库存
- 已整理接口联调文档：`docs/API_TEST.md`

正在进行：

- 当前没有 active change；`add-dish-stock-with-records` 已实现、同步主 specs 并归档
- 下一步按 `docs/API_TEST.md` 做库存与订单完整回归联调，然后再开始 RabbitMQ 订单超时取消 change

## 重要约定

- 后端学习优先，前端只关注如何和后端接口交互。
- 每个功能按链路讲解：请求从哪里来 -> Controller -> Service -> Mapper/XML -> 数据库变化 -> Result 返回 -> 为什么这样设计。
- Controller 不直接调用 Mapper。
- Service 负责业务逻辑、事务、缓存删除、状态流转。
- DTO 接收请求，VO 返回响应，Entity 对应数据库表。
- 所有接口统一返回 `Result<T>`。
- 业务异常使用 `BusinessException`，统一由 `GlobalExceptionHandler` 处理。
- 当前用户 ID 必须由后端从 JWT 解析并放入 `BaseContext`，不能让前端传。
- 请求结束必须清理 `ThreadLocal`，避免线程复用导致用户串号。
- 下单必须使用 `@Transactional`。
- 下单幂等只覆盖 `POST /order/submit`，不扩展到库存、RabbitMQ、支付回调。
- 下单缺少或空白 `Idempotency-Key` 返回 `code=400`；同用户同 key 同请求成功重试返回原 orderId；同 key 不同请求或处理中返回 `code=409`。
- 下单 `request_hash` 基于 `userId`、`remark`、当前购物车内容按 `dishId` 排序后的 `dishId`、`quantity`、`dishPrice`，不包含 `Idempotency-Key`。
- 库存独立于商品表，管理员必须单独初始化 `dish_stock`；未初始化库存的商品不能下单。
- 管理员 SET 库存只设置 `available_stock`，不清空 `locked_stock`。
- 库存生命周期：SET 改 available；LOCK 使 available 减少且 locked 增加；CONFIRM 使 locked 减少且 available 不变；RELEASE 使 locked 减少且 available 增加。
- 写库存流水前必须在同一事务中 `select ... for update` 读取库存行，同时保留 MySQL 条件更新保护。
- 金额计算必须使用 `BigDecimal`，不能使用 `double`。
- SQL 继续使用 MyBatis XML。
- 新功能要小步实现，保持已有功能不破坏，每阶段运行测试或说明验证方式。

## 数据库信息

- 数据库：`demo3_db`
- 本地配置文件：`src/main/resources/application.properties`
- 当前配置中用户名为 `chiye`，密码字段存在于本地配置；后续文档不重复暴露更多敏感信息。

主要表：

- `user`：用户表，用于重新实现注册、登录、JWT 鉴权
- `category`：商品分类表
- `dish`：商品表
- `shopping_cart`：购物车表
- `orders`：订单主表
- `order_detail`：订单明细表
- `order_idempotency`：下单幂等表，唯一索引 `(user_id, idempotency_key)`，状态 `1` 处理中、`2` 已成功、`3` 已失败
- `dish_stock`：库存现状表，`dish_id` 唯一，记录 `available_stock`、`locked_stock`、`version`
- `stock_record`：库存流水表，记录 SET/LOCK/CONFIRM/RELEASE 的 before/after、订单、操作人和备注

订单状态：

- `1`：待支付
- `2`：已支付
- `3`：已完成
- `4`：已取消
- `5`：已接单
- `6`：配送中
- `7`：模拟退款中
- `8`：模拟已退款

订单生命周期：

- `待支付 -> 已支付 -> 已接单 -> 配送中 -> 已完成`
- `待支付 -> 已取消`
- `已支付/已接单 -> 模拟退款中 -> 模拟已退款`

注意：订单状态数字大小不是生命周期顺序，业务判断必须使用 `OrderStatus` 状态机规则，不能用数字大小比较推导进度。当前 `ADMIN` 是第一版最大权限演示操作员，临时承担商家、配送、平台和内部模拟退款操作，不是最终商家角色；本阶段不做用户申请退款。

## 接口列表

分类接口：

- `POST /category`：新增分类
- `GET /category/list`：查询分类列表
- `PUT /category/{id}`：修改分类
- `DELETE /category/{id}`：删除分类

商品接口：

- `POST /dish`：新增商品
- `GET /dish/page`：分页查询商品
- `GET /dish/list`：根据分类查询商品，计划使用 Redis 缓存
- `PUT /dish/{id}`：修改商品
- `PUT /dish/{id}/status`：商品上下架
- `GET /dish/{id}/stock`：管理员查询商品库存
- `PUT /dish/{id}/stock`：管理员设置商品可用库存，body 使用 `availableStock`

购物车接口：

- `POST /cart/add`：加入购物车
- `PUT /cart/update`：修改购物车数量
- `GET /cart/list`：查看当前用户购物车
- `DELETE /cart/clean`：清空当前用户购物车

订单接口：

- `POST /order/submit`：根据购物车提交订单
- `GET /order/{id}`：查询订单详情
- `GET /order/page`：分页查询订单
- `PUT /order/{id}/cancel`：取消订单
- `PUT /order/{id}/accept`：管理员接单
- `PUT /order/{id}/delivery/start`：管理员开始配送
- `PUT /order/{id}/complete`：管理员完成配送中订单
- `PUT /order/{id}/refund/start`：管理员发起内部模拟退款
- `PUT /order/{id}/refund/complete`：管理员完成内部模拟退款

## 关键文件

已有文件：

- `pom.xml`：Maven 依赖配置
- `src/main/resources/application.properties`：项目名、端口、MySQL、MyBatis 基础配置
- `docs/student-manager-design.md`：完整设计文档
- `docs/PROJECT_CONTEXT.md`：当前上下文文件
- `src/main/java/demo3/demo3_068/controller/UserController.java`：用户接口入口
- `src/main/java/demo3/demo3_068/service/impl/UserServiceImpl.java`：用户注册、登录、退出、当前用户业务逻辑
- `src/main/java/demo3/demo3_068/mapper/UserMapper.java`：用户表 Mapper 接口
- `src/main/resources/mapper/UserMapper.xml`：用户表 SQL
- `src/main/java/demo3/demo3_068/utils/JwtUtil.java`：JWT 生成和解析
- `src/main/java/demo3/demo3_068/interceptor/JwtTokenInterceptor.java`：JWT + Redis 登录态校验
- `src/main/java/demo3/demo3_068/config/WebMvcConfig.java`：拦截器注册配置
- `src/main/java/demo3/demo3_068/controller/CategoryController.java`：分类接口入口
- `src/main/java/demo3/demo3_068/service/impl/CategoryServiceImpl.java`：分类查重、存在性检查、删除保护
- `src/main/java/demo3/demo3_068/mapper/CategoryMapper.java`：分类表 Mapper 接口
- `src/main/resources/mapper/CategoryMapper.xml`：分类表 SQL
- `src/main/java/demo3/demo3_068/mapper/DishMapper.java`：当前仅提供分类删除前的商品数量检查
- `src/main/resources/mapper/DishMapper.xml`：商品表 SQL
- `src/main/java/demo3/demo3_068/controller/DishController.java`：商品接口入口
- `src/main/java/demo3/demo3_068/service/impl/DishServiceImpl.java`：商品分类校验、查重、分页、缓存逻辑
- `src/main/java/demo3/demo3_068/controller/CartController.java`：购物车接口入口
- `src/main/java/demo3/demo3_068/service/impl/CartServiceImpl.java`：购物车用户隔离、商品上架校验、数量累加、金额计算
- `src/main/java/demo3/demo3_068/mapper/ShoppingCartMapper.java`：购物车 Mapper 接口
- `src/main/resources/mapper/ShoppingCartMapper.xml`：购物车表 SQL
- `src/main/java/demo3/demo3_068/controller/OrderController.java`：订单接口入口
- `src/main/java/demo3/demo3_068/service/impl/OrderServiceImpl.java`：下单事务、订单详情、分页、状态流转
- `src/main/java/demo3/demo3_068/mapper/OrdersMapper.java`：订单主表 Mapper 接口
- `src/main/resources/mapper/OrdersMapper.xml`：订单主表 SQL
- `src/main/java/demo3/demo3_068/mapper/OrderDetailMapper.java`：订单明细 Mapper 接口
- `src/main/resources/mapper/OrderDetailMapper.xml`：订单明细 SQL
- `src/main/java/demo3/demo3_068/entity/OrderIdempotency.java`：下单幂等记录实体
- `src/main/java/demo3/demo3_068/model/OrderIdempotencyStatus.java`：下单幂等状态枚举
- `src/main/java/demo3/demo3_068/mapper/OrderIdempotencyMapper.java`：下单幂等 Mapper 接口
- `src/main/resources/mapper/OrderIdempotencyMapper.xml`：下单幂等 SQL
- `src/main/java/demo3/demo3_068/service/impl/DishStockServiceImpl.java`：库存设置、锁定、确认、释放和流水写入
- `src/main/java/demo3/demo3_068/mapper/DishStockMapper.java`：库存 Mapper 接口
- `src/main/resources/mapper/DishStockMapper.xml`：库存行锁读取与条件更新 SQL
- `src/main/java/demo3/demo3_068/mapper/StockRecordMapper.java`：库存流水 Mapper 接口
- `src/main/resources/mapper/StockRecordMapper.xml`：库存流水 SQL
- `docs/API_TEST.md`：接口联调命令和数据库验证 SQL

计划创建：

- `sql/schema.sql`
- `src/main/java/demo3/demo3_068/common/Result.java`
- `src/main/java/demo3/demo3_068/common/PageResult.java`
- `src/main/java/demo3/demo3_068/common/BaseContext.java`
- `src/main/java/demo3/demo3_068/exception/BusinessException.java`
- `src/main/java/demo3/demo3_068/exception/GlobalExceptionHandler.java`
- `src/main/java/demo3/demo3_068/controller/*Controller.java`
- `src/main/java/demo3/demo3_068/service/*Service.java`
- `src/main/java/demo3/demo3_068/service/impl/*ServiceImpl.java`
- `src/main/java/demo3/demo3_068/mapper/*Mapper.java`
- `src/main/resources/mapper/*Mapper.xml`
- `src/main/java/demo3/demo3_068/dto/*DTO.java`
- `src/main/java/demo3/demo3_068/vo/*VO.java`
- `src/main/java/demo3/demo3_068/entity/*.java`

## 常用命令

启动项目：

```bash
./mvnw spring-boot:run
```

运行测试：

```bash
./mvnw test
```

查看项目文件：

```bash
find src -maxdepth 4 -type f | sort
```

## 已知问题

- 用户注册登录代码已实现，但尚未启动服务做 HTTP 接口联调。
- 登录接口依赖 Redis；联调前需要确认本地或 Docker Redis 正在运行。
- 管理员和普通用户权限第一版可先预留 `role`，是否严格区分待确认。

## 下一步计划

1. 按 `docs/API_TEST.md` 从登录、商品、库存初始化、下单、支付、取消完整跑一遍，重点验证库存 SET/LOCK/CONFIRM/RELEASE 流水。
2. 后续 RabbitMQ 超时取消可以复用当前 `releaseLockedStock` 语义：只对仍为待支付且状态条件更新成功的订单释放锁定库存。
3. 后续可进入前端页面或继续补更细的商家/平台权限。

## 待用户补充

- 是否第一版就区分管理员和普通用户权限。
- 是否需要原生 HTML/CSS/JS 页面，还是先只做后端接口。
- Redis 是否使用本地环境。
