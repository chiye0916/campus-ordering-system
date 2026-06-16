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

## 1. 准备普通用户和管理员

注册普通用户，已注册过可跳过：

```bash
curl -X POST "$BASE_URL/user/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"zhangsan","password":"123456","nickname":"aa"}'
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

注册管理员用户，已注册过可跳过：

```bash
curl -X POST "$BASE_URL/user/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456","nickname":"管理员"}'
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

提交订单：

```bash
curl -X POST "$BASE_URL/order/submit" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
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

## 6. 订单异常边界测试

已完成订单不能取消：

```bash
curl -X PUT "$BASE_URL/order/$ORDER_ID/cancel" \
  -H "Authorization: Bearer $USER_TOKEN"
```

期望结果：

```text
code=409
message=已完成订单不能取消
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
  -d '{"remark":"空购物车测试"}'
```

期望结果：

```text
code=409
message=购物车为空，不能下单
```

商品改价后，旧购物车快照不能直接下单：

```bash
curl -X POST "$BASE_URL/cart/add" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d "{\"dishId\":$DISH_ID,\"quantity\":1}"

curl -X PUT "$BASE_URL/dish/$DISH_ID" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"categoryId\":$CATEGORY_ID,\"name\":\"测试鸡腿饭\",\"price\":25.00,\"image\":\"\",\"description\":\"接口测试商品-再次改价\",\"status\":1}"

curl -X POST "$BASE_URL/order/submit" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d '{"remark":"价格变化测试"}'
```

期望结果：

```text
code=409
message=商品信息已变化，请刷新购物车后重新下单
```

如果后续还要继续使用该商品测试，可以先清空购物车，再重新加入商品，重新加入时购物车会同步当前商品名称和价格：

```bash
curl -X DELETE "$BASE_URL/cart/clean" \
  -H "Authorization: Bearer $USER_TOKEN"

curl -X POST "$BASE_URL/cart/add" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d "{\"dishId\":$DISH_ID,\"quantity\":1}"
```

下架商品不能加入购物车：

```bash
curl -X PUT "$BASE_URL/dish/$DISH_ID/status" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"status":0}'

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

## 9. 状态说明

订单状态：

```text
1 待支付
2 已支付
3 已完成
4 已取消
```

合法流转：

```text
待支付 -> 已支付 -> 已完成
待支付 -> 已取消
已支付 -> 已取消
```
