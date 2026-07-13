# API Test

本文档用于本地联调简化版校园点餐系统。建议从上到下执行，先验证权限，再验证完整业务闭环。

## 0. 前置条件

确认 MySQL、Redis 容器正在运行：

```bash
docker ps
```

应该能看到：

```text
mysql8
redis7
```

第四阶段订单超时取消还需要 RabbitMQ。若本地还没有启动，可使用：

```bash
docker run -d --name rabbitmq-demo3 \
  -p 5672:5672 -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=demo3 \
  -e RABBITMQ_DEFAULT_PASS=12345 \
  rabbitmq:3-management
```

管理后台：

```text
http://127.0.0.1:15672
username: demo3
password: 12345
```

启动项目：

```bash
./mvnw spring-boot:run
```

设置请求地址：

```bash
BASE_URL="http://127.0.0.1:8080"
```

如果你的数据库是在邮箱验证码功能之前创建的，需要先给 `user` 表补邮箱字段：

```bash
docker exec -it mysql8 mysql -u chiye -p1234 demo3_db
```

```sql
alter table user add column email varchar(128) null after username;
alter table user add unique key uk_user_email (email);
exit;
```

如果你的数据库是在订单超时取消功能之前创建的，需要补 `order_timeout_outbox` 表，并初始化系统审计用户。也可以直接参考 `sql/schema.sql` 执行完整建表脚本。

```sql
CREATE TABLE IF NOT EXISTS order_timeout_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    message_id VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    expire_time DATETIME NOT NULL,
    status TINYINT NOT NULL COMMENT '1:PENDING, 2:PUBLISHING, 3:SENT, 4:FAILED',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_time DATETIME NOT NULL,
    publish_claim_time DATETIME,
    sent_time DATETIME,
    last_error VARCHAR(512),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_timeout_outbox_order_id (order_id),
    UNIQUE KEY uk_order_timeout_outbox_message_id (message_id),
    KEY idx_order_timeout_outbox_due (status, next_retry_time, retry_count),
    KEY idx_order_timeout_outbox_publish_claim_time (status, publish_claim_time),
    KEY idx_order_timeout_outbox_expire_time (expire_time)
);

INSERT INTO `user` (username, email, password, nickname, role)
SELECT 'system_timeout', NULL, '12345', '订单超时系统', 'SYSTEM'
WHERE NOT EXISTS (
    SELECT 1 FROM `user` WHERE username = 'system_timeout'
);
```

如果你的数据库是在支付回调幂等功能之前创建的，需要补 `payment_callback_record` 表：

```sql
CREATE TABLE IF NOT EXISTS payment_callback_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_record_id BIGINT,
    order_id BIGINT,
    trade_no VARCHAR(64) NOT NULL,
    callback_no VARCHAR(64) NOT NULL,
    third_trade_no VARCHAR(128),
    pay_status VARCHAR(32) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    callback_time DATETIME NOT NULL,
    process_status TINYINT NOT NULL COMMENT '1:PROCESSING, 2:PROCESSED, 3:DUPLICATE, 4:FAILED, 5:IGNORED',
    failure_reason VARCHAR(255),
    raw_payload TEXT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_payment_callback_no (callback_no),
    KEY idx_payment_callback_trade_no (trade_no),
    KEY idx_payment_callback_payment_record_id (payment_record_id),
    KEY idx_payment_callback_order_id (order_id)
);
```

## 1. 准备普通用户和管理员

发送注册邮箱验证码：

```bash
curl -X POST "$BASE_URL/user/email/code" \
  -H "Content-Type: application/json" \
  -d '{"email":"你的邮箱@qq.com"}'
```

查看邮箱，复制 6 位验证码：

```bash
EMAIL_CODE="把邮箱里的验证码粘贴到这里"
```

注册普通用户，已注册过可跳过。注意：现在注册需要邮箱和验证码：

```bash
curl -X POST "$BASE_URL/user/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"zhangsan\",\"email\":\"你的邮箱@qq.com\",\"password\":\"123456\",\"emailCode\":\"$EMAIL_CODE\",\"nickname\":\"aa\"}"
```

登录普通用户：

```bash
curl -X POST "$BASE_URL/user/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"zhangsan","password":"123456"}'
```

复制返回的 `data.token`：

```bash
USER_TOKEN="把 zhangsan 登录返回的 token 粘贴到这里"
```

注册管理员用户时同样需要先获取邮箱验证码。Demo 当前不开放前端直接注册管理员，建议先注册为普通用户，再通过数据库授权为管理员：

```bash
curl -X POST "$BASE_URL/user/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"email\":\"另一个邮箱@qq.com\",\"password\":\"123456\",\"emailCode\":\"$EMAIL_CODE\",\"nickname\":\"管理员\"}"
```

把 `admin` 改成管理员：

```bash
docker exec -it mysql8 mysql -u chiye -p1234 demo3_db
```

```sql
update user set role = 'ADMIN' where username = 'admin';
select id, username, nickname, role from user;
exit;
```

重新登录管理员。注意：修改 role 之后必须重新登录，因为 JWT 里的 role 是登录时生成的。

```bash
curl -X POST "$BASE_URL/user/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'
```

复制返回的 `data.token`：

```bash
ADMIN_TOKEN="把 admin 登录返回的 token 粘贴到这里"
```

验证两个身份：

```bash
curl "$BASE_URL/user/me" \
  -H "Authorization: Bearer $USER_TOKEN"

curl "$BASE_URL/user/me" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

期望结果：

```text
zhangsan 的 role 是 USER
admin 的 role 是 ADMIN
```

## 2. 权限边界测试

普通用户新增分类，应该失败：

```bash
curl -X POST "$BASE_URL/category" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d '{"name":"权限测试分类","sort":99}'
```

期望结果：

```json
{"code":403,"message":"无管理员权限","data":null}
```

管理员新增分类，应该成功：

```bash
curl -X POST "$BASE_URL/category" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"name":"权限测试分类","sort":99}'
```

普通用户访问商品管理分页，应该失败：

```bash
curl "$BASE_URL/dish/page?page=1&pageSize=10" \
  -H "Authorization: Bearer $USER_TOKEN"
```

期望结果：

```json
{"code":403,"message":"无管理员权限","data":null}
```

未登录访问受保护接口，应该失败：

```bash
curl "$BASE_URL/category/list"
```

期望结果：

```json
{"code":401,"message":"请先登录","data":null}
```

## 3. 管理员准备分类和商品

新增一个测试分类：

```bash
curl -X POST "$BASE_URL/category" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"name":"测试主食","sort":10}'
```

复制返回的 `data`：

```bash
CATEGORY_ID="把新增分类返回的 data 粘贴到这里"
```

查询分类列表：

```bash
curl "$BASE_URL/category/list" \
  -H "Authorization: Bearer $USER_TOKEN"
```

新增一个测试商品：

```bash
curl -X POST "$BASE_URL/dish" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"categoryId\":$CATEGORY_ID,\"name\":\"测试鸡腿饭\",\"price\":20.00,\"image\":\"\",\"description\":\"接口测试商品\",\"status\":1}"
```

复制返回的 `data`：

```bash
DISH_ID="把新增商品返回的 data 粘贴到这里"
```

库存独立于商品创建，需要管理员单独初始化可用库存。请求体字段必须使用 `availableStock`：

```bash
curl -X PUT "$BASE_URL/dish/$DISH_ID/stock" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"availableStock":100,"remark":"接口测试初始化库存"}'
```

查询库存：

```bash
curl "$BASE_URL/dish/$DISH_ID/stock" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

期望结果：

```text
availableStock=100
lockedStock=0
```

普通用户设置库存应该失败：

```bash
curl -X PUT "$BASE_URL/dish/$DISH_ID/stock" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d '{"availableStock":50}'
```

期望结果：

```json
{"code":403,"message":"无管理员权限","data":null}
```

管理员分页查询商品：

```bash
curl "$BASE_URL/dish/page?page=1&pageSize=10" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

普通用户按分类查询上架商品：

```bash
curl "$BASE_URL/dish/list?categoryId=$CATEGORY_ID" \
  -H "Authorization: Bearer $USER_TOKEN"
```

查看 Redis 商品列表缓存：

```bash
docker exec -it redis7 redis-cli get "dish:list:category:$CATEGORY_ID"
```

缓存说明：`/dish/list` 按 `dish:list:category:{categoryId}` 做 Cache Aside 缓存；非空列表默认 TTL 为 30 分钟，已有分类但没有上架商品时缓存 JSON `[]`，默认 TTL 为 5 分钟。Redis 读取或写入失败时接口会回退数据库结果，不因缓存失败报错；缓存 JSON 损坏时会尝试删除坏 key，再查数据库并重写缓存。新增、修改、上下架商品会删除受影响分类的列表缓存；修改分类时会同时删除旧分类和新分类缓存。管理员库存设置/查询不会删除 `/dish/list` 缓存，因为当前 `DishVO` 列表不包含库存字段。若缓存删除失败，数据库修改仍会成功，旧列表最多保留到 TTL 过期，属于 Cache Aside 的最终一致性取舍。

修改商品价格：

```bash
curl -X PUT "$BASE_URL/dish/$DISH_ID" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"categoryId\":$CATEGORY_ID,\"name\":\"测试鸡腿饭\",\"price\":22.00,\"image\":\"\",\"description\":\"接口测试商品-改价\",\"status\":1}"
```

改价后再次查询商品列表，应该看到价格变成 `22.00`：

```bash
curl "$BASE_URL/dish/list?categoryId=$CATEGORY_ID" \
  -H "Authorization: Bearer $USER_TOKEN"
```

## 4. 购物车测试

清空当前用户购物车：

```bash
curl -X DELETE "$BASE_URL/cart/clean" \
  -H "Authorization: Bearer $USER_TOKEN"
```

加入购物车：

```bash
curl -X POST "$BASE_URL/cart/add" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d "{\"dishId\":$DISH_ID,\"quantity\":1}"
```

重复加入同一商品，数量应该累加：

```bash
curl -X POST "$BASE_URL/cart/add" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d "{\"dishId\":$DISH_ID,\"quantity\":1}"
```

查看购物车：

```bash
curl "$BASE_URL/cart/list" \
  -H "Authorization: Bearer $USER_TOKEN"
```

修改购物车数量：

```bash
curl -X PUT "$BASE_URL/cart/update" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d "{\"dishId\":$DISH_ID,\"quantity\":2}"
```

数量小于 1，应该失败：

```bash
curl -X PUT "$BASE_URL/cart/update" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d "{\"dishId\":$DISH_ID,\"quantity\":0}"
```

期望结果：

```text
code=400
```

删除购物车中的这个商品项：

```bash
curl -X DELETE "$BASE_URL/cart/$DISH_ID" \
  -H "Authorization: Bearer $USER_TOKEN"
```

删除后再次查看购物车，应该不再包含这个商品：

```bash
curl "$BASE_URL/cart/list" \
  -H "Authorization: Bearer $USER_TOKEN"
```

为了继续测试下单流程，把商品重新加入购物车：

```bash
curl -X POST "$BASE_URL/cart/add" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d "{\"dishId\":$DISH_ID,\"quantity\":2}"
```

## 5. 订单主流程测试

生成本次提交使用的幂等键。同一次提交动作重试时复用同一个值；新的提交动作换一个新值：

```bash
ORDER_SUBMIT_KEY="$(uuidgen)"
```

提交订单：

```bash
curl -X POST "$BASE_URL/order/submit" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Idempotency-Key: $ORDER_SUBMIT_KEY" \
  -d '{"remark":"少放辣"}'
```

复制返回的 `data`：

```bash
ORDER_ID="把提交订单返回的 data 粘贴到这里"
```

提交订单后购物车应该为空：

```bash
curl "$BASE_URL/cart/list" \
  -H "Authorization: Bearer $USER_TOKEN"
```

提交订单会锁库存：`available_stock` 减少购物车数量，`locked_stock` 增加购物车数量，并写入 `LOCK` 流水：

```bash
docker exec -it mysql8 mysql -u chiye -p1234 demo3_db
```

```sql
select dish_id, available_stock, locked_stock, version
from dish_stock
where dish_id = 你的商品ID;

select dish_id, order_id, change_type, change_quantity,
       available_before, available_after, locked_before, locked_after, operator_id, remark
from stock_record
where order_id = 你的订单ID
order by id;
exit;
```

期望结果：

```text
dish_stock.available_stock 从 100 变为 98
dish_stock.locked_stock 从 0 变为 2
stock_record 存在一条 LOCK，change_quantity=2，order_id=ORDER_ID
```

重复使用同一个 `ORDER_SUBMIT_KEY` 重试同一次提交，应该直接返回第一次的订单 ID，不创建新订单：

```bash
curl -X POST "$BASE_URL/order/submit" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Idempotency-Key: $ORDER_SUBMIT_KEY" \
  -d '{"remark":"少放辣"}'
```

期望结果：

```text
code=200
data 等于上面的 ORDER_ID
orders 表不会新增第二笔同 key 订单
stock_record 不会新增第二条 LOCK
```

查询订单详情：

```bash
curl "$BASE_URL/order/$ORDER_ID" \
  -H "Authorization: Bearer $USER_TOKEN"
```

普通用户分页查询自己的订单：

```bash
curl "$BASE_URL/order/page?page=1&pageSize=10" \
  -H "Authorization: Bearer $USER_TOKEN"
```

管理员分页查询全部订单：

```bash
curl "$BASE_URL/order/page?page=1&pageSize=10" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

发起模拟支付：

```bash
curl -X PUT "$BASE_URL/order/$ORDER_ID/pay" \
  -H "Authorization: Bearer $USER_TOKEN"
```

期望结果中 `data.orderStatus` 仍然是 `1`，`data.payStatus` 是 `1`，并返回模拟支付流水号：

```json
{
  "orderId": 1,
  "orderNumber": "订单号",
  "orderStatus": 1,
  "amount": 44.00,
  "tradeNo": "PAY...",
  "payStatus": 1,
  "requestTime": "发起支付时间"
}
```

保存返回的 `tradeNo` 和一个唯一回调号：

```bash
TRADE_NO="把 pay 接口返回的 data.tradeNo 粘贴到这里"
CALLBACK_NO="CB$(date +%Y%m%d%H%M%S)"
```

查看支付流水，应该存在一条 `MOCK` 支付中记录，订单仍为待支付：

```bash
docker exec -it mysql8 mysql -u chiye -p1234 demo3_db
```

```sql
select id, order_id, order_number, user_id, amount, pay_channel, trade_no, status, request_time, success_time, callback_time
from payment_record
where order_id = 你的订单ID
order by id desc;

select id, status, pay_time
from orders
where id = 你的订单ID;
exit;
```

期望结果：

```text
pay_channel=MOCK
payment_record.status=1
trade_no 以 PAY 开头且不为空
orders.status=1
success_time 为空
```

发送模拟支付成功回调。这个接口模拟第三方支付平台通知，不需要普通用户 JWT：

```bash
curl -X POST "$BASE_URL/payment/mock/callback" \
  -H "Content-Type: application/json" \
  -d "{\"tradeNo\":\"$TRADE_NO\",\"callbackNo\":\"$CALLBACK_NO\",\"thirdTradeNo\":\"THIRD-$CALLBACK_NO\",\"payStatus\":\"SUCCESS\",\"amount\":44.00,\"callbackTime\":\"$(date '+%Y-%m-%dT%H:%M:%S')\"}"
```

期望结果：

```text
code=200
```

回调成功后，订单变为已支付，支付流水变为支付成功，并写入回调记录：

```sql
select id, status, pay_time
from orders
where id = 你的订单ID;

select id, order_id, amount, pay_channel, trade_no, third_trade_no, status, success_time, callback_time
from payment_record
where trade_no = '你的 TRADE_NO';

select id, payment_record_id, order_id, trade_no, callback_no, third_trade_no,
       pay_status, amount, process_status, failure_reason
from payment_callback_record
where callback_no = '你的 CALLBACK_NO';
```

期望结果：

```text
orders.status=2
payment_record.status=2
payment_callback_record.process_status=2
third_trade_no 已保存
```

成功回调会确认锁定库存：`locked_stock` 减少订单数量，`available_stock` 不变，并写入 `CONFIRM` 流水：

```sql
select dish_id, available_stock, locked_stock, version
from dish_stock
where dish_id = 你的商品ID;

select dish_id, order_id, change_type, change_quantity,
       available_before, available_after, locked_before, locked_after, operator_id, remark
from stock_record
where order_id = 你的订单ID
order by id;
```

期望结果：

```text
dish_stock.available_stock 仍为 98
dish_stock.locked_stock 从 2 变为 0
stock_record 中新增 CONFIRM，change_quantity=2
```

重复发送同一个 `callbackNo`，应该幂等返回成功，不重复更新订单或库存：

```bash
curl -X POST "$BASE_URL/payment/mock/callback" \
  -H "Content-Type: application/json" \
  -d "{\"tradeNo\":\"$TRADE_NO\",\"callbackNo\":\"$CALLBACK_NO\",\"thirdTradeNo\":\"THIRD-CHANGED\",\"payStatus\":\"SUCCESS\",\"amount\":44.00,\"callbackTime\":\"$(date '+%Y-%m-%dT%H:%M:%S')\"}"
```

```sql
select callback_no, count(*) as count
from payment_callback_record
where callback_no = '你的 CALLBACK_NO'
group by callback_no;

select change_type, count(*) as count
from stock_record
where order_id = 你的订单ID
  and change_type = 'CONFIRM'
group by change_type;
```

期望结果：

```text
同一个 callback_no 只有 1 行
CONFIRM 仍然只有 1 条
```

已支付订单不能直接取消；本阶段没有用户申请退款接口，只支持管理员内部模拟退款：

```bash
curl -X PUT "$BASE_URL/order/$ORDER_ID/cancel" \
  -H "Authorization: Bearer $USER_TOKEN"
```

期望结果：

```text
code=409
message=只有待支付订单才能取消
```

已支付订单不能直接完成：

```bash
curl -X PUT "$BASE_URL/order/$ORDER_ID/complete" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

期望结果：

```text
code=409
message=只有配送中订单才能完成
```

管理员接单：

```bash
curl -X PUT "$BASE_URL/order/$ORDER_ID/accept" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

管理员开始配送：

```bash
curl -X PUT "$BASE_URL/order/$ORDER_ID/delivery/start" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

管理员完成订单：

```bash
curl -X PUT "$BASE_URL/order/$ORDER_ID/complete" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

再次查询订单详情，状态应该是 `3`：

```bash
curl "$BASE_URL/order/$ORDER_ID" \
  -H "Authorization: Bearer $USER_TOKEN"
```

管理员内部模拟退款验证可以使用另一笔已支付或已接单订单：

```bash
curl -X PUT "$BASE_URL/order/$REFUND_ORDER_ID/refund/start" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

curl -X PUT "$BASE_URL/order/$REFUND_ORDER_ID/refund/complete" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

期望状态依次为 `7` 模拟退款中、`8` 模拟已退款。`6` 配送中和 `3` 已完成订单不能在本阶段发起退款。

## 6. 订单异常边界测试

已完成订单不能取消：

```bash
curl -X PUT "$BASE_URL/order/$ORDER_ID/cancel" \
  -H "Authorization: Bearer $USER_TOKEN"
```

期望结果：

```text
code=409
message=只有待支付订单才能取消
```

已完成订单不能重复支付：

```bash
curl -X PUT "$BASE_URL/order/$ORDER_ID/pay" \
  -H "Authorization: Bearer $USER_TOKEN"
```

期望结果：

```text
code=409
message=只有待支付订单才能发起支付
```

Redis 订单状态锁验证：同一个订单的并发状态流转只能有一个请求进入业务逻辑。先重新创建一个待支付订单，得到新的 `LOCK_ORDER_ID`：

```bash
curl -X DELETE "$BASE_URL/cart/clean" \
  -H "Authorization: Bearer $USER_TOKEN"

curl -X POST "$BASE_URL/cart/add" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d "{\"dishId\":$DISH_ID,\"quantity\":1}"

curl -X POST "$BASE_URL/order/submit" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"remark":"Redis锁并发测试"}'
```

复制返回的 `data`：

```bash
LOCK_ORDER_ID="把新订单 ID 粘贴到这里"
```

快速重复支付同一个订单：

```bash
curl -X PUT "$BASE_URL/order/$LOCK_ORDER_ID/pay" \
  -H "Authorization: Bearer $USER_TOKEN" &
curl -X PUT "$BASE_URL/order/$LOCK_ORDER_ID/pay" \
  -H "Authorization: Bearer $USER_TOKEN" &
wait
```

期望结果：

```text
最多一个请求返回 code=200
其他请求返回 code=409，message 可能是“订单处理中，请稍后重试”
payment_record 中该订单最多只有一条 status=1 的 MOCK 支付中记录
```

查看支付流水：

```sql
select order_id, pay_channel, status, count(*) as count
from payment_record
where order_id = 你的 LOCK_ORDER_ID
group by order_id, pay_channel, status;
```

支付回调边界验证可以复用一笔新的待支付订单和它的 `TRADE_NO`。以下场景都应该写入 `payment_callback_record`，但只有第一笔有效成功回调会更新订单和写 `CONFIRM`：

```bash
BAD_AMOUNT_CALLBACK_NO="CB-AMOUNT-$(date +%s)"
curl -X POST "$BASE_URL/payment/mock/callback" \
  -H "Content-Type: application/json" \
  -d "{\"tradeNo\":\"$TRADE_NO\",\"callbackNo\":\"$BAD_AMOUNT_CALLBACK_NO\",\"payStatus\":\"SUCCESS\",\"amount\":0.01,\"callbackTime\":\"$(date '+%Y-%m-%dT%H:%M:%S')\"}"

UNKNOWN_TRADE_CALLBACK_NO="CB-UNKNOWN-$(date +%s)"
curl -X POST "$BASE_URL/payment/mock/callback" \
  -H "Content-Type: application/json" \
  -d "{\"tradeNo\":\"PAY-NOT-FOUND\",\"callbackNo\":\"$UNKNOWN_TRADE_CALLBACK_NO\",\"payStatus\":\"SUCCESS\",\"amount\":44.00,\"callbackTime\":\"$(date '+%Y-%m-%dT%H:%M:%S')\"}"
```

期望：两个请求都返回 `code=200`，但 `process_status=4`，不会更新订单、支付成功状态或库存。

失败支付回调会把当前 `PAYING` 支付流水改成 `FAILED`，订单仍保持待支付，锁定库存不释放也不确认；用户可以再次调用 `PUT /order/{id}/pay` 创建新的 `PAYING` 流水：

```bash
FAILED_CALLBACK_NO="CB-FAILED-$(date +%s)"
curl -X POST "$BASE_URL/payment/mock/callback" \
  -H "Content-Type: application/json" \
  -d "{\"tradeNo\":\"$TRADE_NO\",\"callbackNo\":\"$FAILED_CALLBACK_NO\",\"payStatus\":\"FAILED\",\"amount\":44.00,\"callbackTime\":\"$(date '+%Y-%m-%dT%H:%M:%S')\"}"

curl -X PUT "$BASE_URL/order/$ORDER_ID/pay" \
  -H "Authorization: Bearer $USER_TOKEN"
```

期望：失败回调的 `payment_callback_record.process_status=2`，这是“回调处理成功”，不是“支付成功”；`payment_record.status=3` 表示支付失败，重新发起支付会返回新的 `tradeNo`。

手工插入或保留一条最近的 `PROCESSING` 回调记录时，同一个 `callbackNo` 再次请求应该返回 `code=409`，不会被当成幂等成功；把 `update_time` 改到 5 分钟以前后再请求，系统会重新尝试处理并最终进入终态：

```sql
insert into payment_callback_record (
  trade_no, callback_no, pay_status, amount, callback_time, process_status, raw_payload
) values (
  '你的 TRADE_NO', 'CB-PROCESSING-RECENT', 'SUCCESS', 44.00, now(), 1, '{}'
);

update payment_callback_record
set update_time = date_sub(now(), interval 10 minute)
where callback_no = 'CB-PROCESSING-RECENT';
```

对已成功、已失败或已关闭的支付流水再发送新 `callbackNo`，期望只记录 `DUPLICATE` 或 `IGNORED` 终态，不反转支付状态、不更新订单、不写新的 `CONFIRM`。超时取消后到达的成功回调也只记录 no-op，不会把已取消订单改回已支付。

库存并发验证：把商品可用库存设置为一个很小的值，例如 3，然后并发提交多笔数量为 1 的订单。成功订单数不应该超过可用库存，失败请求应返回库存不足或处理中相关业务错误。

```bash
curl -X PUT "$BASE_URL/dish/$DISH_ID/stock" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"availableStock":3,"remark":"库存并发验证"}'
```

准备多个普通用户或为同一用户依次清购物车、加购、提交。真实压测建议使用不同用户和不同购物车；本 Demo 可以用 HTTP 工具并发发起多组：

```bash
for i in 1 2 3 4 5; do
  (
    curl -X DELETE "$BASE_URL/cart/clean" -H "Authorization: Bearer $USER_TOKEN" >/dev/null
    curl -X POST "$BASE_URL/cart/add" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $USER_TOKEN" \
      -d "{\"dishId\":$DISH_ID,\"quantity\":1}" >/dev/null
    curl -X POST "$BASE_URL/order/submit" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $USER_TOKEN" \
      -H "Idempotency-Key: $(uuidgen)" \
      -d "{\"remark\":\"库存并发验证-$i\"}"
  ) &
done
wait
```

验证库存没有被超卖：

```sql
select dish_id, available_stock, locked_stock
from dish_stock
where dish_id = 你的商品ID;

select change_type, count(*) as count, sum(change_quantity) as quantity
from stock_record
where dish_id = 你的商品ID
  and change_type = 'LOCK'
group by change_type;
```

期望结果：

```text
available_stock 不小于 0
成功 LOCK 的总数量不超过设置的 availableStock
```

检查 Redis 锁 key 已释放：

```bash
docker exec -it redis7 redis-cli exists "lock:order:status:$LOCK_ORDER_ID"
```

期望结果：

```text
0
```

已支付订单不能直接取消：

```bash
curl -X PUT "$BASE_URL/order/$LOCK_ORDER_ID/cancel" \
  -H "Authorization: Bearer $USER_TOKEN"
```

期望结果：

```text
code=409
message=只有待支付订单才能取消
```

取消待支付订单会释放锁定库存。重新创建一笔待支付订单：

```bash
curl -X POST "$BASE_URL/cart/add" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d "{\"dishId\":$DISH_ID,\"quantity\":1}"

curl -X POST "$BASE_URL/order/submit" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"remark":"取消释放库存测试"}'
```

复制返回的 `data`：

```bash
CANCEL_ORDER_ID="把待取消订单 ID 粘贴到这里"
```

取消订单：

```bash
curl -X PUT "$BASE_URL/order/$CANCEL_ORDER_ID/cancel" \
  -H "Authorization: Bearer $USER_TOKEN"
```

验证库存和流水：

```sql
select dish_id, available_stock, locked_stock, version
from dish_stock
where dish_id = 你的商品ID;

select dish_id, order_id, change_type, change_quantity,
       available_before, available_after, locked_before, locked_after, operator_id, remark
from stock_record
where order_id = 你的 CANCEL_ORDER_ID
order by id;
```

期望结果：

```text
订单取消前 LOCK 使 available_stock -1、locked_stock +1
取消后 RELEASE 使 available_stock +1、locked_stock -1
stock_record 中该订单有 LOCK 和 RELEASE 各一条
重复取消不会新增第二条 RELEASE
```

普通用户不能完成订单：

```bash
curl -X PUT "$BASE_URL/order/$ORDER_ID/complete" \
  -H "Authorization: Bearer $USER_TOKEN"
```

期望结果：

```json
{"code":403,"message":"无管理员权限","data":null}
```

空购物车不能下单：

```bash
curl -X DELETE "$BASE_URL/cart/clean" \
  -H "Authorization: Bearer $USER_TOKEN"

curl -X POST "$BASE_URL/order/submit" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"remark":"空购物车测试"}'
```

期望结果：

```text
code=409
message=购物车为空，不能下单
```

缺少或空白 `Idempotency-Key` 不能下单：

```bash
curl -X POST "$BASE_URL/order/submit" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d '{"remark":"缺少幂等键测试"}'

curl -X POST "$BASE_URL/order/submit" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Idempotency-Key:    " \
  -d '{"remark":"空白幂等键测试"}'
```

期望结果：

```text
code=400
message=Idempotency-Key 不能为空
```

同一个用户复用同一个 `Idempotency-Key` 提交不同内容，应该返回 `409`，不创建新订单：

```bash
CONFLICT_SUBMIT_KEY="$(uuidgen)"

curl -X DELETE "$BASE_URL/cart/clean" \
  -H "Authorization: Bearer $USER_TOKEN"

curl -X POST "$BASE_URL/cart/add" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d "{\"dishId\":$DISH_ID,\"quantity\":1}"

curl -X POST "$BASE_URL/order/submit" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Idempotency-Key: $CONFLICT_SUBMIT_KEY" \
  -d '{"remark":"第一次内容"}'

curl -X POST "$BASE_URL/cart/add" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d "{\"dishId\":$DISH_ID,\"quantity\":1}"

curl -X POST "$BASE_URL/order/submit" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Idempotency-Key: $CONFLICT_SUBMIT_KEY" \
  -d '{"remark":"第二次不同内容"}'
```

期望结果：

```text
第二次 order/submit 返回 code=409
message=Idempotency-Key 已被不同下单请求使用
orders 表不会因为第二次请求新增订单
```

商品改价后，购物车应该显示最新价格，并按最新价格下单：

```bash
curl -X POST "$BASE_URL/cart/add" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d "{\"dishId\":$DISH_ID,\"quantity\":1}"

curl -X PUT "$BASE_URL/dish/$DISH_ID" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"categoryId\":$CATEGORY_ID,\"name\":\"测试鸡腿饭\",\"price\":25.00,\"image\":\"\",\"description\":\"接口测试商品-再次改价\",\"status\":1}"

curl "$BASE_URL/cart/list" \
  -H "Authorization: Bearer $USER_TOKEN"

curl -X POST "$BASE_URL/order/submit" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"remark":"价格变化测试"}'
```

期望结果：

```text
cart/list 中该商品 dishPrice 变为 25.00，并出现 changeMessage=价格已更新
order/submit 返回 code=200
```

如果后续还要继续使用该商品测试，可以重新加入商品：

```bash
curl -X POST "$BASE_URL/cart/add" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d "{\"dishId\":$DISH_ID,\"quantity\":1}"
```

下架商品不能加入购物车；如果商品已经在购物车里，购物车会显示为不可购买，下单会失败：

先确保购物车中有该商品：

```bash
curl -X POST "$BASE_URL/cart/add" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d "{\"dishId\":$DISH_ID,\"quantity\":1}"
```

下架商品：

```bash
curl -X PUT "$BASE_URL/dish/$DISH_ID/status" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"status":0}'
```

查看购物车，应该看到 `available=false` 和 `changeMessage=商品已下架，请移出购物车`：

```bash
curl "$BASE_URL/cart/list" \
  -H "Authorization: Bearer $USER_TOKEN"
```

提交订单应该失败：

```bash
curl -X POST "$BASE_URL/order/submit" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"remark":"下架商品测试"}'
```

期望结果：

```text
code=409
message=购物车中存在已下架商品，请先移出购物车
```

下架商品不能再次加入购物车：

```bash
curl -X POST "$BASE_URL/cart/add" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d "{\"dishId\":$DISH_ID,\"quantity\":1}"
```

期望结果：

```text
code=409
message=商品已下架，不能加入购物车
```

测试结束后可以重新上架：

```bash
curl -X PUT "$BASE_URL/dish/$DISH_ID/status" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"status":1}'
```

请求体 JSON 格式错误，应该返回 `code=400`：

```bash
curl -X POST "$BASE_URL/order/submit" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"remark":'
```

期望结果：

```text
code=400
message=请求体格式错误
```

## 7. 登录态测试

退出登录：

```bash
curl -X POST "$BASE_URL/user/logout" \
  -H "Authorization: Bearer $USER_TOKEN"
```

退出后继续使用旧 token，应该失败：

```bash
curl "$BASE_URL/user/me" \
  -H "Authorization: Bearer $USER_TOKEN"
```

期望结果：

```json
{"code":401,"message":"登录已失效","data":null}
```

后续如果还要继续测试普通用户接口，需要重新登录 `zhangsan` 并更新 `USER_TOKEN`。

## 8. 数据库验证 SQL

进入 MySQL：

```bash
docker exec -it mysql8 mysql -u chiye -p1234 demo3_db
```

查看用户：

```sql
select id, username, nickname, role, create_time from user;
```

查看分类：

```sql
select id, name, sort, create_time, update_time from category order by sort, id;
```

查看商品：

```sql
select id, category_id, name, price, status, create_time, update_time from dish order by id;
```

查看库存：

```sql
select id, dish_id, available_stock, locked_stock, version, create_time, update_time
from dish_stock
order by dish_id;
```

查看库存流水：

```sql
select id, dish_id, order_id, change_type, change_quantity,
       available_before, available_after, locked_before, locked_after,
       operator_id, remark, create_time
from stock_record
order by id;
```

查看购物车：

```sql
select id, user_id, dish_id, dish_name, dish_price, quantity from shopping_cart order by id;
```

查看订单主表：

```sql
select id, number, user_id, status, amount, remark, order_time, pay_time, cancel_time, complete_time
from orders
order by id;
```

查看订单明细：

```sql
select id, order_id, dish_id, dish_name, dish_price, quantity, amount
from order_detail
order by id;
```

查看下单幂等记录：

```sql
select id, user_id, idempotency_key, request_hash, order_id, status, create_time, update_time
from order_idempotency
order by id;
```

查看订单超时 outbox：

```sql
select id, order_id, message_id, expire_time, status, retry_count,
       next_retry_time, publish_claim_time, sent_time, last_error, create_time, update_time
from order_timeout_outbox
order by id desc;
```

状态说明：

```text
1 PROCESSING / 处理中
2 SUCCEEDED / 已成功
3 FAILED / 已失败
```

订单超时 outbox 状态说明：

```text
1 PENDING / 待发布
2 PUBLISHING / 已被发布器 claim
3 SENT / RabbitMQ 已确认接收
4 FAILED / 发布失败，等待重试或达到最大重试次数后人工检查
```

## 9. 订单超时自动取消验证

默认超时时间是 15 分钟：

```properties
order.timeout.delay=15m
```

本地验证可以临时改成 30 秒：

```properties
order.timeout.delay=30s
```

注意：RabbitMQ 队列参数中的 `x-message-ttl` 在队列声明时确定。如果你已经用 15 分钟启动过应用，再改成 30 秒，可能需要在 RabbitMQ 管理后台删除下面两个队列，让应用重启后重新声明：

```text
order.timeout.delay.queue
order.timeout.cancel.queue
```

验证步骤：

1. 确认 RabbitMQ、MySQL、Redis 都已启动。
2. 启动应用。
3. 加购物车并提交订单，但不要支付。
4. 立即查看 `order_timeout_outbox`，应看到一条该订单的记录，状态最终变为 `3 SENT`。
5. 等待超时时间后查看订单、库存和库存流水。

SQL 检查：

```sql
select id, order_id, message_id, expire_time, status, retry_count, sent_time, last_error
from order_timeout_outbox
where order_id = 你的订单ID;

select id, status, cancel_time
from orders
where id = 你的订单ID;

select dish_id, available_stock, locked_stock, version
from dish_stock
where dish_id = 你的商品ID;

select sr.id, sr.dish_id, sr.order_id, sr.change_type, sr.change_quantity,
       sr.operator_id, u.username, sr.remark
from stock_record sr
left join `user` u on u.id = sr.operator_id
where sr.order_id = 你的订单ID
order by sr.id;
```

期望结果：

```text
order_timeout_outbox.status=3
orders.status=4，cancel_time 不为空
dish_stock.locked_stock 减少，下单锁定的库存回到 available_stock
stock_record 新增 RELEASE
RELEASE.operator_id 对应 user.username=system_timeout
RELEASE.remark=订单超时自动取消释放库存
```

RabbitMQ 队列检查：

```text
Exchanges:
- order.timeout.delay.exchange
- order.timeout.dead.exchange

Queues:
- order.timeout.delay.queue
- order.timeout.cancel.queue
```

`order.timeout.delay.queue` 应绑定到 delay exchange，并配置：

```text
x-message-ttl = 当前 order.timeout.delay 对应毫秒数
x-dead-letter-exchange = order.timeout.dead.exchange
x-dead-letter-routing-key = order.timeout.cancel
```

说明：TTL 从 outbox publisher 成功发布到 RabbitMQ 后开始计算，不是从订单创建瞬间开始。如果 RabbitMQ 或 publisher 暂时不可用，实际取消时间可能晚于 `expire_time`；本阶段通过 outbox 保证发送意图可重试，不做额外订单兜底扫描。

## 10. 状态说明

订单状态：

```text
1 待支付
2 已支付
3 已完成
4 已取消
5 已接单
6 配送中
7 模拟退款中
8 模拟已退款
```

合法流转：

```text
待支付 -> 已支付 -> 已接单 -> 配送中 -> 已完成
待支付 -> 已取消
已支付 -> 模拟退款中 -> 模拟已退款
已接单 -> 模拟退款中 -> 模拟已退款
```

说明：状态数字大小不是生命周期顺序，不能用 `status > 2` 之类的判断推导订单进度。当前 `ADMIN` 是第一版最大权限演示操作员，用于接单、配送、完成、内部模拟退款；它不是最终商家角色。本 change 不提供用户申请退款接口。
