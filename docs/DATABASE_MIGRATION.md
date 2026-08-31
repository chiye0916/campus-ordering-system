# 数据库迁移说明

## 唯一真相源

正式数据库结构由 `src/main/resources/db/migration` 下的 Flyway 迁移管理：

- `V1__baseline_schema.sql`：空数据库的完整 14 表基线。
- `V2__upgrade_legacy_database.sql`：为旧数据库安全补齐 `order_status_history`、`refund_request` 和系统操作人。
- `V3__refresh_order_status_comment.sql`：更新 `orders.status` 的状态说明。

根目录 `sql/schema.sql` 仅保留为结构快照和不支持 Flyway 环境的一次性兼容脚本，不参与应用启动和 Testcontainers 初始化。新增结构变化必须先创建新的 Flyway 迁移，再同步快照；不能只修改快照。

## 空数据库

空数据库不需要 baseline。应用启动时会依次执行 V1、V2、V3，并创建 `flyway_schema_history`。

主配置默认：

```properties
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=false
```

默认关闭自动 baseline 是为了防止应用误连未知的非空数据库后静默跳过基线。

## 当前旧数据库升级策略

当前 `demo3_db` 是已有业务数据的非空数据库，已有 13 张业务表和手工创建的 `order_status_history`，但没有 `refund_request` 和 Flyway 历史表。

本地 profile 明确开启：

```properties
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=1
```

第一次以 `local` profile 启动时：

1. Flyway 为现有非空库记录版本 1 baseline，不执行 V1，不重建现有表。
2. 执行 V2；`CREATE TABLE IF NOT EXISTS` 保留手工创建的 `order_status_history`，并创建缺失的 `refund_request`。
3. 执行 V3，仅更新 `orders.status` 字段注释。
4. 已有订单、用户、库存、支付和状态历史数据不会被删除或覆盖。

baseline 只适用于已确认具备 V1 核心结构的旧库。不要对来源不明或结构残缺的非空数据库直接开启 `baseline-on-migrate`，应先做只读结构审计。

## 本地启动与 Mock 支付

本地开发使用：

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

`local` profile 会开启：

```properties
spring.flyway.baseline-on-migrate=true
payment.mock.enabled=true
```

也可以不启用 profile，单独通过环境变量控制：

```bash
FLYWAY_BASELINE_ON_MIGRATE=true PAYMENT_MOCK_ENABLED=true ./mvnw spring-boot:run
```

`/payment/mock/callback` 只是本地模拟第三方通知的接口，不具备真实支付签名、验签和渠道安全能力。生产环境必须保持 `payment.mock.enabled=false`；主配置默认即为关闭。

## 集成测试

Testcontainers 启动空 MySQL 后，由 Spring Boot/Flyway 执行正式迁移，不再使用 `.withInitScript("sql/schema.sql")`：

```bash
./mvnw verify -Pintegration-test
```

测试同时验证空库迁移和旧库 baseline 无损升级。
