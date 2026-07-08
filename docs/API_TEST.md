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

支付订单：

```bash
curl -X PUT "$BASE_URL/order/$ORDER_ID/pay" \
  -H "Authorization: Bearer $USER_TOKEN"
```

期望结果中 `data.status` 应该是 `2`，并返回支付关键信息：

```json
{
  "orderId": 1,
  "orderNumber": "订单号",
  "status": 2,
  "amount": 44.00,
  "payTime": "支付时间"
}
```

查看支付流水，应该存在一条 `MOCK` 支付成功记录：

```bash
docker exec -it mysql8 mysql -u chiye -p1234 demo3_db
```

```sql
select id, order_id, order_number, user_id, amount, pay_channel, trade_no, status, request_time, success_time
from payment_record
where order_id = 你的订单ID
order by id desc;
exit;
```

期望结果：

```text
pay_channel=MOCK
status=2
trade_no 以 PAY 开头且不为空
success_time 不为空
```

支付成功会确认锁定库存：`locked_stock` 减少订单数量，`available_stock` 不变，并写入 `CONFIRM` 流水：

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
message=只有待支付订单才能支付
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
其他请求返回 code=409，message 可能是“订单处理中，请稍后重试”或“只有待支付订单才能支付”
payment_record 中该订单最多只有一条 status=2 的 MOCK 支付成功记录
```

查看支付流水：

```sql
select order_id, pay_channel, status, count(*) as count
from payment_record
where order_id = 你的 LOCK_ORDER_ID
group by order_id, pay_channel, status;
```

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

状态说明：

```text
1 PROCESSING / 处理中
2 SUCCEEDED / 已成功
3 FAILED / 已失败
```

## 9. 状态说明

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
