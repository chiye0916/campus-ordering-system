package demo3.demo3_068.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import demo3.demo3_068.common.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
abstract class BaseIntegrationTest {

    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("demo3_it")
            .withUsername("demo3")
            .withPassword("demo3")
            .withInitScript("sql/schema.sql");

    static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE)
            .withExposedPorts(6379);

    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:3-management");

    static {
        MYSQL.start();
        REDIS.start();
        RABBITMQ.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected StringRedisTemplate stringRedisTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }

    @BeforeEach
    void cleanIntegrationState() {
        cleanDatabase();
        stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    protected Long createUser(String username, String role) {
        String encodedPassword = passwordEncoder.encode("password123");
        return insert("user", Map.of(
                "username", username,
                "email", username + "@example.test",
                "password", encodedPassword,
                "nickname", username,
                "role", role));
    }

    protected String login(String username) throws Exception {
        JsonNode response = postJson("/user/login", Map.of(
                "username", username,
                "password", "password123"));
        return response.path("data").path("token").asText();
    }

    protected Long createCategory(String name) {
        return insert("category", Map.of("name", name, "sort", 1));
    }

    protected Long createDish(Long categoryId, String name, String price, int status) {
        return insert("dish", Map.of(
                "category_id", categoryId,
                "name", name,
                "price", new BigDecimal(price),
                "image", "https://example.test/" + name + ".png",
                "description", name + " description",
                "status", status));
    }

    protected void setStock(Long dishId, int availableStock, int lockedStock) {
        insert("dish_stock", Map.of(
                "dish_id", dishId,
                "available_stock", availableStock,
                "locked_stock", lockedStock,
                "version", 0));
    }

    protected void addCart(String token, Long dishId, int quantity) throws Exception {
        postJsonWithToken("/cart/add", token, Map.of("dishId", dishId, "quantity", quantity));
    }

    protected Long submitOrder(String token, String idempotencyKey, String remark) throws Exception {
        JsonNode response = postJsonWithTokenAndHeader(
                "/order/submit",
                token,
                Map.of("remark", remark),
                "Idempotency-Key",
                idempotencyKey);
        return response.path("data").asLong();
    }

    protected JsonNode startPayment(String token, Long orderId) throws Exception {
        return putJsonWithToken("/order/" + orderId + "/pay", token, Map.of());
    }

    protected JsonNode postPaymentCallback(String tradeNo, String callbackNo, String status, String amount) throws Exception {
        return postJson("/payment/mock/callback", Map.of(
                "tradeNo", tradeNo,
                "callbackNo", callbackNo,
                "thirdTradeNo", "THIRD-" + callbackNo,
                "payStatus", status,
                "amount", amount,
                "callbackTime", LocalDateTime.now().toString()));
    }

    protected JsonNode getJson(String url) throws Exception {
        MvcResult result = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected JsonNode getJsonWithToken(String url, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(url).header("Authorization", Constants.TOKEN_PREFIX + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected JsonNode postJson(String url, Object body) throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected JsonNode postJsonWithToken(String url, String token, Object body) throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .header("Authorization", Constants.TOKEN_PREFIX + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected JsonNode postJsonWithTokenAndHeader(String url, String token, Object body, String headerName, String headerValue)
            throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .header("Authorization", Constants.TOKEN_PREFIX + token)
                        .header(headerName, headerValue)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected JsonNode putJsonWithToken(String url, String token, Object body) throws Exception {
        MvcResult result = mockMvc.perform(put(url)
                        .header("Authorization", Constants.TOKEN_PREFIX + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected JsonNode deleteJsonWithToken(String url, String token) throws Exception {
        MvcResult result = mockMvc.perform(delete(url)
                        .header("Authorization", Constants.TOKEN_PREFIX + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected Integer intCell(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    protected Long longCell(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    protected String stringCell(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, String.class, args);
    }

    protected BigDecimal decimalCell(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
    }

    protected String dishCacheKey(Long categoryId) {
        return "dish:list:category:" + categoryId;
    }

    private void cleanDatabase() {
        jdbcTemplate.execute("set foreign_key_checks = 0");
        for (String table : new String[]{
                "payment_callback_record",
                "payment_record",
                "order_status_history",
                "stock_record",
                "refund_request",
                "order_detail",
                "order_timeout_outbox",
                "order_idempotency",
                "shopping_cart",
                "orders",
                "dish_stock",
                "dish",
                "category",
                "user"
        }) {
            jdbcTemplate.update("delete from " + table);
        }
        jdbcTemplate.execute("set foreign_key_checks = 1");
    }

    private Long insert(String table, Map<String, ?> values) {
        Number key = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName(table)
                .usingGeneratedKeyColumns("id")
                .usingColumns(values.keySet().toArray(String[]::new))
                .executeAndReturnKey(values);
        return key.longValue();
    }
}
