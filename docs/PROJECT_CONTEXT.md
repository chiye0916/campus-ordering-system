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
- `/dish/list` 已接入增强版 Redis Cache Aside 缓存：按分类 key 缓存，非空列表 TTL 默认 30 分钟，空列表 JSON `[]` TTL 默认 5 分钟，Redis 读写失败回退数据库，损坏 JSON 会删除后回源并重写
- 商品新增、修改、上下架后会通过 `DishListCacheService` 删除对应分类商品列表缓存；修改商品分类会删除旧分类和新分类缓存
- 已实现购物车模块：加入购物车、修改数量、查看购物车、清空购物车
- 购物车使用 `BaseContext` 获取当前用户 ID，前端不传 `userId`
- 已实现订单模块：提交订单、订单详情、订单分页、支付、取消、接单、开始配送、完成订单、用户退款申请、管理员退款审核/完成、管理员内部模拟退款兜底
- 下单使用 `@Transactional`，保证订单主表、订单明细、购物车清空同时成功或回滚
- `POST /order/submit` 已加入 MySQL 下单幂等：客户端必须传 `Idempotency-Key`，成功仍返回 `Result<Long>` 的订单 ID
- 已新增 `order_idempotency` 表，用 `(user_id, idempotency_key)` 唯一约束记录下单幂等状态
- 已补充订单支付发起接口：`PUT /order/{id}/pay`
- 已新增库存模块：`dish_stock` 保存可用/锁定库存，`stock_record` 保存 SET/LOCK/CONFIRM/RELEASE 库存流水
- 已新增商家/管理员库存接口：`GET /dish/{id}/stock`、`PUT /dish/{id}/stock`，设置字段为 `availableStock`
- 下单会先创建待支付订单获得 `order_id`，再按 dishId 聚合并升序锁库存、写 `LOCK` 流水；幂等重复返回原订单时不会重复锁库存
- `PUT /order/{id}/pay` 只创建或复用 `MOCK`、`PAYING` 支付流水，返回 `tradeNo`，不更新订单为已支付，也不确认库存
- 已新增 `POST /payment/mock/callback` 模拟第三方支付回调接口；成功回调才会把订单从待支付改为已支付并确认锁定库存、写 `CONFIRM` 流水
- 已新增 `payment_callback_record` 表，以 `callback_no` 唯一约束实现回调请求幂等，记录未知流水、金额不匹配、失败支付、重复成功、终态 no-op 和超时后迟到回调
- 支付回调使用 `tradeNo` 匹配 `payment_record`，使用 `BigDecimal.compareTo` 校验金额，`SUCCESS/FAILED/CLOSED` 支付流水为终态，后续回调不能反转支付状态
- 手工取消和超时取消待支付订单成功后，会在同一事务内关闭当前 `MOCK`、`PAYING` 支付流水
- 取消待支付订单会在订单状态条件更新成功后释放锁定库存、写 `RELEASE` 流水；重复取消不会重复释放库存
- 已实现 RabbitMQ TTL + DLX 订单超时取消：下单事务内写 `order_timeout_outbox`，定时 publisher claim 后发布，RabbitMQ confirm 后标记 `SENT`
- 超时取消 consumer 只取消仍为待支付的订单；非待支付、已取消、缺失订单作为幂等 no-op
- 超时取消复用 Redis 订单状态锁和数据库 `where status = PENDING_PAYMENT` 条件更新，状态更新成功后才释放库存
- 自动超时释放库存会写 `RELEASE` 流水，operator 使用 `system_timeout` 系统审计用户，remark 为“订单超时自动取消释放库存”
- `SYSTEM` 角色不会被当作普通管理员，且 `system_timeout` 不能通过正常登录 API 登录
- 已细化角色边界：`USER`、`MERCHANT`、`DELIVERY`、`ADMIN`、`SYSTEM` 由 `Role` 枚举和 `PermissionChecker` 集中处理；未知角色在 JWT/权限解析时 fail closed，不会默认成任何有效角色
- 公开注册只创建 `USER`，请求中携带 `role` 会被拒绝；`MERCHANT`、`DELIVERY`、`ADMIN` 本阶段通过 SQL 或测试 helper 创建
- 客户端购物车、下单、发起模拟支付、待支付自取消只允许 `USER`；`/dish/list` 不新增角色限制
- 分类、菜品、库存管理允许 `MERCHANT` 和 `ADMIN`；接单允许 `MERCHANT` 和 `ADMIN`；开始配送和完成订单允许 `DELIVERY` 和 `ADMIN`；退款申请审核和内部模拟退款兜底仍仅 `ADMIN`
- 已新增用户退款申请流程：`USER` 可为自己的 `PAID`/`ACCEPTED` 订单创建退款申请，服务端生成 `refund_no` 并快照订单号与金额；`ADMIN` 可查看全部申请、审批通过、拒绝或完成退款
- 已新增 `refund_request` 表，状态为 `PENDING_REVIEW`、`APPROVED`、`REJECTED`、`COMPLETED`；同一订单唯一一条退款申请，拒绝或完成后也不能再次申请
- 退款申请审核通过会在同一事务中将 `refund_request` 从 `PENDING_REVIEW` 改为 `APPROVED`，并将订单从 `PAID`/`ACCEPTED` 改为 `REFUNDING`；完成退款会将申请改为 `COMPLETED`，并将订单从 `REFUNDING` 改为 `REFUNDED`
- 退款申请创建、审批和完成均不恢复库存、不写 `stock_record`；旧的 `/order/{id}/refund/start` 和 `/order/{id}/refund/complete` 保留为无退款申请订单的管理员内部兜底接口
- 订单列表按工作台收敛：`USER` 看自己的全部状态订单，`MERCHANT` 看全局 `PAID`/`ACCEPTED`，`DELIVERY` 看全局 `ACCEPTED`/`DELIVERING`，`ADMIN` 看全部；`SYSTEM` 不允许普通 HTTP 可见性
- 订单详情按角色历史范围收敛：`MERCHANT` 可看 `PAID`、`ACCEPTED`、`DELIVERING`、`COMPLETED`、`REFUNDING`、`REFUNDED`；`DELIVERY` 可看 `ACCEPTED`、`DELIVERING`、`COMPLETED`；退款操作仍不授予 `MERCHANT`
- 已新增 Testcontainers 集成回归套件基础：`./mvnw test` 继续只跑快速单元测试且不依赖 Docker，`./mvnw verify -Pintegration-test` 通过 Failsafe 运行 `*IT`，使用容器 MySQL、Redis（`GenericContainer` + `redis:7-alpine`）和轻量 RabbitMQ 上下文配置
- 集成回归套件覆盖关键链路：`/dish/list` Redis 缓存、下单幂等与库存锁定、支付回调幂等与库存确认、用户退款申请到管理员审批完成；RabbitMQ 超时 TTL/DLX/监听重试/自动取消释放库存不在本阶段集成测试范围
- 已整理接口联调文档：`docs/API_TEST.md`

正在进行：

- 当前没有未归档的 OpenSpec change。
- `refine-roles-and-admin-permissions`、`add-testcontainers-regression-suite`、`add-observability-reliability-baseline`、`add-user-refund-request-flow` 均已实现、同步主 specs 并归档。
- 默认 `./mvnw test` 继续运行快速单元测试且不依赖 Docker；`./mvnw verify -Pintegration-test` 显式运行 MySQL/Redis/RabbitMQ 容器支撑的 `*IT` 集成测试。
- 后续可讨论下一阶段优化方向：接口体验、权限错误信息、前端演示页、可靠性细节、压测或部署配置。

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
- 下单幂等只覆盖 `POST /order/submit`，支付回调幂等由 `payment_callback_record.callback_no` 单独负责。
- 下单缺少或空白 `Idempotency-Key` 返回 `code=400`；同用户同 key 同请求成功重试返回原 orderId；同 key 不同请求或处理中返回 `code=409`。
- 下单 `request_hash` 基于 `userId`、`remark`、当前购物车内容按 `dishId` 排序后的 `dishId`、`quantity`、`dishPrice`，不包含 `Idempotency-Key`。
- 库存独立于商品表，管理员必须单独初始化 `dish_stock`；未初始化库存的商品不能下单。
- `MERCHANT` 或 `ADMIN` SET 库存只设置 `available_stock`，不清空 `locked_stock`。
- 库存生命周期：SET 改 available；LOCK 使 available 减少且 locked 增加；CONFIRM 使 locked 减少且 available 不变；RELEASE 使 locked 减少且 available 增加。
- 写库存流水前必须在同一事务中 `select ... for update` 读取库存行，同时保留 MySQL 条件更新保护。
- 金额计算必须使用 `BigDecimal`，不能使用 `double`。
- SQL 继续使用 MyBatis XML。
- 新功能要小步实现，保持已有功能不破坏，每阶段运行测试或说明验证方式。
- 测试分层：`./mvnw test` 是快速单元测试，不依赖 Docker；`./mvnw verify -Pintegration-test` 才运行 Testcontainers 集成回归测试并需要 Docker。
- 订单超时取消使用 RabbitMQ TTL + DLX，不使用 delayed-message 插件。
- 订单超时消息通过 `order_timeout_outbox` 提供 at-least-once 投递语义，不承诺 exactly-once；consumer 必须依靠 orderId、订单状态和条件更新保持幂等。
- RabbitMQ TTL 从 publisher 成功发布后开始计算；RabbitMQ 或 publisher 不可用时，实际取消可能晚于 `expire_time`。
- 模拟支付是两步流程：用户调用 `PUT /order/{id}/pay` 发起或复用支付流水；模拟第三方调用 `POST /payment/mock/callback` 通知 `SUCCESS` 或 `FAILED`。
- 支付回调 `process_status` 含义：`1 PROCESSING` 处理中、`2 PROCESSED` 已完成业务处理、`3 DUPLICATE` 新回调号但业务结果已成功、`4 FAILED` 回调校验失败、`5 IGNORED` 业务状态不允许执行。
- `payStatus=FAILED` 与 `process_status=FAILED` 不是同一概念；失败支付回调被成功接收并把支付流水改为失败时，回调记录可以是 `process_status=PROCESSED`。
- `/dish/list` 保持先校验分类存在，再读分类维度缓存；缺失分类继续返回“分类不存在”，且不会读写商品列表缓存。
- 商品列表缓存是加速层，不是数据源。Redis get/set/delete 失败只记录日志并继续业务；删除失败可能导致旧列表短暂可见，靠 TTL 最终清理。
- 商家/管理员库存 SET/查询不删除 `/dish/list` 缓存，因为当前 `DishVO` 列表响应不包含库存。

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
- `payment_record`：模拟支付流水表，记录订单、金额、支付渠道、交易号、状态、请求/成功/回调时间
- `payment_callback_record`：模拟第三方支付回调记录表，`callback_no` 唯一，记录回调处理状态、原始 payload、失败原因，可在未知 `tradeNo` 时保留空的支付/订单引用
- `order_timeout_outbox`：订单超时消息 outbox，记录 orderId/messageId/payload/expireTime/status/retry/claim/sent/lastError
- `refund_request`：退款申请业务记录表，`order_id` 和 `refund_no` 唯一，记录订单快照、原因、状态、审核人、审核/完成时间

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

退款申请状态：

- `PENDING_REVIEW`：待审核
- `APPROVED`：已通过，订单已进入模拟退款中
- `REJECTED`：已拒绝，v1 中同一订单不能再次申请
- `COMPLETED`：已完成，订单已进入模拟已退款

注意：订单状态数字大小不是生命周期顺序，业务判断必须使用 `OrderStatus` 状态机规则，不能用数字大小比较推导进度。当前角色边界是轻量版本：`MERCHANT` 负责目录/库存/接单，`DELIVERY` 负责配送履约，`ADMIN` 作为平台兜底、审核退款申请并保留内部模拟退款兜底权限。

## 接口列表

分类接口：

- `POST /category`：新增分类
- `GET /category/list`：查询分类列表
- `PUT /category/{id}`：修改分类
- `DELETE /category/{id}`：删除分类

商品接口：

- `POST /dish`：新增商品
- `GET /dish/page`：分页查询商品
- `GET /dish/list`：根据分类查询上架商品，使用 Redis 分类列表缓存
- `PUT /dish/{id}`：修改商品
- `PUT /dish/{id}/status`：商品上下架
- `GET /dish/{id}/stock`：商家或管理员查询商品库存
- `PUT /dish/{id}/stock`：商家或管理员设置商品可用库存，body 使用 `availableStock`

购物车接口：

- `POST /cart/add`：加入购物车
- `PUT /cart/update`：修改购物车数量
- `GET /cart/list`：查看当前用户购物车
- `DELETE /cart/clean`：清空当前用户购物车

订单接口：

- `POST /order/submit`：根据购物车提交订单
- `GET /order/{id}`：查询订单详情
- `GET /order/page`：分页查询订单
- `PUT /order/{id}/pay`：发起或复用模拟支付，返回 `tradeNo` 和 `PAYING` 状态
- `PUT /order/{id}/cancel`：取消订单
- `PUT /order/{id}/accept`：商家或管理员接单
- `PUT /order/{id}/delivery/start`：配送员或管理员开始配送
- `PUT /order/{id}/complete`：配送员或管理员完成配送中订单
- `PUT /order/{id}/refund/start`：管理员发起内部模拟退款
- `PUT /order/{id}/refund/complete`：管理员完成内部模拟退款

退款申请接口：

- `POST /refund/request`：普通用户为自己的已支付/已接单订单创建退款申请，body 只传 `orderId` 和 `reason`
- `GET /refund/my/page`：普通用户分页查看自己的退款申请，支持 `status`
- `GET /refund/{id}`：普通用户查看自己的退款申请详情，管理员可查看任意退款申请详情
- `GET /refund/page`：管理员分页查看全部退款申请，支持 `status`
- `PUT /refund/{id}/approve`：管理员审批通过待审核退款申请，并将订单改为模拟退款中
- `PUT /refund/{id}/reject`：管理员拒绝待审核退款申请，body 传 `rejectReason`，不改变订单状态
- `PUT /refund/{id}/complete`：管理员完成已通过退款申请，并将订单改为模拟已退款

支付接口：

- `POST /payment/mock/callback`：模拟第三方支付回调，body 包含 `tradeNo`、`callbackNo`、可选 `thirdTradeNo`、`payStatus`、`amount`、`callbackTime`

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
- `src/main/java/demo3/demo3_068/service/impl/DishServiceImpl.java`：商品分类校验、查重、分页、列表缓存编排
- `src/main/java/demo3/demo3_068/service/DishListCacheService.java`：`/dish/list` Redis key、JSON、TTL、故障回退、损坏缓存恢复和删除封装
- `src/main/java/demo3/demo3_068/config/DishCacheProperties.java`：商品列表缓存 TTL 配置，默认 `listTtl=30m`、`emptyListTtl=5m`
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
- `src/main/java/demo3/demo3_068/config/RabbitMqConfig.java`：RabbitMQ TTL/DLX、队列绑定、listener retry、RabbitTemplate 配置
- `src/main/java/demo3/demo3_068/config/OrderTimeoutProperties.java`：订单超时、outbox publisher、listener retry 配置属性
- `src/main/java/demo3/demo3_068/entity/OrderTimeoutOutbox.java`：订单超时 outbox 实体
- `src/main/java/demo3/demo3_068/model/OrderTimeoutOutboxStatus.java`：订单超时 outbox 状态枚举
- `src/main/java/demo3/demo3_068/mapper/OrderTimeoutOutboxMapper.java`：订单超时 outbox Mapper 接口
- `src/main/resources/mapper/OrderTimeoutOutboxMapper.xml`：订单超时 outbox SQL
- `src/main/java/demo3/demo3_068/service/impl/OrderTimeoutOutboxServiceImpl.java`：下单事务内创建 timeout outbox
- `src/main/java/demo3/demo3_068/service/impl/OrderTimeoutOutboxPublisher.java`：定时扫描、claim、发布、confirm、失败重试、stale 恢复
- `src/main/java/demo3/demo3_068/service/impl/OrderTimeoutCancelListener.java`：RabbitMQ 超时取消消费者
- `src/main/java/demo3/demo3_068/controller/PaymentController.java`：模拟支付回调接口入口
- `src/main/java/demo3/demo3_068/service/PaymentService.java`：支付服务接口
- `src/main/java/demo3/demo3_068/service/impl/PaymentServiceImpl.java`：支付回调幂等、金额校验、支付/订单/库存事务处理
- `src/main/java/demo3/demo3_068/entity/PaymentRecord.java`：支付流水实体
- `src/main/java/demo3/demo3_068/entity/PaymentCallbackRecord.java`：支付回调记录实体
- `src/main/java/demo3/demo3_068/mapper/PaymentRecordMapper.java`：支付流水 Mapper 接口
- `src/main/resources/mapper/PaymentRecordMapper.xml`：支付流水 SQL
- `src/main/java/demo3/demo3_068/mapper/PaymentCallbackRecordMapper.java`：支付回调记录 Mapper 接口
- `src/main/resources/mapper/PaymentCallbackRecordMapper.xml`：支付回调记录 SQL
- `src/main/java/demo3/demo3_068/model/PaymentStatus.java`：支付流水状态枚举
- `src/main/java/demo3/demo3_068/model/PaymentCallbackProcessStatus.java`：支付回调处理状态枚举
- `src/main/java/demo3/demo3_068/dto/MockPaymentCallbackDTO.java`：模拟支付回调请求 DTO
- `src/main/java/demo3/demo3_068/common/PermissionChecker.java`：角色权限集中校验
- `src/main/java/demo3/demo3_068/model/Role.java`：`USER`、`MERCHANT`、`DELIVERY`、`ADMIN`、`SYSTEM` 角色枚举
- `src/main/java/demo3/demo3_068/observability/TraceIdFilter.java`：HTTP 请求 traceId 生成、校验、响应头返回与 MDC 清理
- `src/main/java/demo3/demo3_068/observability/TraceContext.java`：HTTP 外任务和 RabbitMQ 链路的 trace 上下文工具
- `src/main/java/demo3/demo3_068/observability/BusinessMetrics.java`：业务指标记录和 outbox 状态 gauge
- `src/main/resources/static/index.html`、`src/main/resources/static/app.js`、`src/main/resources/static/styles.css`：本地静态演示页面
- `src/test/java/demo3/demo3_068/integration/*IT.java`：Testcontainers 集成回归测试
- `openspec/specs/*/spec.md`：当前主规格
- `openspec/changes/archive/*`：已完成并归档的 OpenSpec changes
- `scripts/k6/*.js`：手动 smoke/load 辅助脚本，不属于 Maven 门禁
- `docs/API_TEST.md`：接口联调命令和数据库验证 SQL

## 常用命令

启动项目：

```bash
./mvnw spring-boot:run
```

运行测试：

```bash
./mvnw test
```

运行集成测试（需要 Docker）：

```bash
./mvnw verify -Pintegration-test
```

查看项目文件：

```bash
find src -maxdepth 4 -type f | sort
```

## 已知问题

- `docs/student-manager-design.md` 是早期设计文档，部分角色、状态和表数量描述已落后于当前实现；以后以 `docs/PROJECT_CONTEXT.md`、`openspec/specs/*/spec.md` 和代码为准。
- 注册发送邮箱验证码依赖邮件配置；本地未配置邮件服务时会返回“邮箱服务未配置”。
- 本地完整运行应用依赖 MySQL、Redis、RabbitMQ；快速单元测试不依赖 Docker，集成测试需要 Docker。
- 权限失败提示目前较通用，例如部分非管理员场景也返回“无管理员权限”，后续可做体验优化。

## 下一步计划

1. 按用户优先级选择下一阶段优化点，建议从接口体验、错误提示、前端演示页或可靠性细节中挑一个小步推进。
2. 若引入新功能，继续按 OpenSpec 流程先提出 change，再实现、测试、同步 specs、归档。
3. 需要大改订单、支付、库存或超时取消时，优先补充/运行对应单元测试；涉及真实 MySQL/Redis/RabbitMQ 行为时运行 `./mvnw verify -Pintegration-test`。

## 待用户补充

- 下一阶段最想优化的方向和优先级。
- 本地 MySQL、Redis、RabbitMQ 是否继续使用当前 Docker/本机配置。
- 邮箱验证码是否需要改为更适合本地学习的模拟模式或开发开关。
