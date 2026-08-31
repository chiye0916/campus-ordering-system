package demo3.demo3_068.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationIT extends BaseIntegrationTest {

    @Test
    void emptyDatabaseIsMigratedToLatestVersion() {
        assertThat(longCell("""
                select count(*)
                from information_schema.tables
                where table_schema = database()
                  and table_name in (
                    'user', 'category', 'dish', 'dish_stock', 'shopping_cart', 'orders',
                    'order_status_history', 'refund_request', 'order_detail', 'stock_record',
                    'order_idempotency', 'payment_record', 'payment_callback_record', 'order_timeout_outbox'
                  )
                """)).isEqualTo(14L);
        assertThat(longCell("select count(*) from flyway_schema_history where success = 1")).isEqualTo(3L);
    }

    @Test
    void legacyNonEmptyDatabaseIsBaselinedAndUpgradedWithoutDataLoss() throws Exception {
        try (MySQLContainer<?> legacy = new MySQLContainer<>("mysql:8.4")
                .withDatabaseName("demo3_legacy")
                .withUsername("demo3")
                .withPassword("demo3")) {
            legacy.start();
            prepareLegacyDatabase(legacy);

            Flyway flyway = Flyway.configure()
                    .dataSource(legacy.getJdbcUrl(), legacy.getUsername(), legacy.getPassword())
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .baselineVersion("1")
                    .cleanDisabled(true)
                    .load();

            flyway.migrate();

            try (Connection connection = DriverManager.getConnection(
                    legacy.getJdbcUrl(), legacy.getUsername(), legacy.getPassword())) {
                assertThat(queryLong(connection, "select count(*) from orders where number = 'LEGACY-ORDER-1'"))
                        .isEqualTo(1L);
                assertThat(queryLong(connection, "select count(*) from order_status_history where operation = 'ORDER_SUBMIT'"))
                        .isEqualTo(1L);
                assertThat(queryLong(connection, "select count(*) from information_schema.tables "
                        + "where table_schema = database() and table_name = 'refund_request'"))
                        .isEqualTo(1L);
                assertThat(queryLong(connection, "select count(*) from flyway_schema_history "
                        + "where version in ('2', '3') and success = 1"))
                        .isEqualTo(2L);
            }
        }
    }

    private void prepareLegacyDatabase(MySQLContainer<?> legacy) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                legacy.getJdbcUrl(), legacy.getUsername(), legacy.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table user (
                      id bigint primary key auto_increment,
                      username varchar(64) not null,
                      email varchar(128),
                      password varchar(255) not null,
                      nickname varchar(64),
                      role varchar(32) not null default 'USER',
                      create_time datetime not null default current_timestamp,
                      update_time datetime not null default current_timestamp on update current_timestamp,
                      unique key uk_user_username (username),
                      unique key uk_user_email (email)
                    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                    """);
            statement.execute("""
                    create table orders (
                      id bigint primary key auto_increment,
                      number varchar(64) not null,
                      user_id bigint not null,
                      status tinyint not null comment '1:待支付, 2:已支付, 3:已完成, 4:已取消',
                      amount decimal(10,2) not null,
                      remark varchar(255),
                      order_time datetime not null,
                      pay_time datetime,
                      cancel_time datetime,
                      complete_time datetime,
                      unique key uk_orders_number (number),
                      key idx_orders_user_id (user_id),
                      key idx_orders_status (status)
                    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                    """);
            statement.execute("""
                    create table order_status_history (
                      id bigint primary key auto_increment,
                      order_id bigint not null,
                      order_number varchar(64) not null,
                      user_id bigint not null,
                      old_status tinyint,
                      new_status tinyint not null,
                      operation varchar(64) not null,
                      operator_id bigint,
                      operator_role varchar(32) not null,
                      reason varchar(255) not null,
                      trace_id varchar(64),
                      create_time datetime not null default current_timestamp,
                      key idx_order_status_history_order_id_create_time_id (order_id, create_time, id),
                      key idx_order_status_history_trace_id (trace_id)
                    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
                    """);
            statement.execute("insert into user (username, password, role) values ('legacy_user', 'x', 'USER')");
            statement.execute("""
                    insert into orders (number, user_id, status, amount, order_time)
                    values ('LEGACY-ORDER-1', 1, 1, 20.00, now())
                    """);
            statement.execute("""
                    insert into order_status_history (
                      order_id, order_number, user_id, old_status, new_status,
                      operation, operator_id, operator_role, reason
                    ) values (1, 'LEGACY-ORDER-1', 1, null, 1, 'ORDER_SUBMIT', 1, 'USER', 'legacy history')
                    """);
        }
    }

    private long queryLong(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
