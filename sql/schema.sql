CREATE TABLE IF NOT EXISTS user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    email VARCHAR(128),
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(64),
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_username (username),
    UNIQUE KEY uk_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    sort INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_category_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS dish (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    image VARCHAR(255),
    description VARCHAR(255),
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0:下架, 1:上架',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_dish_category_id (category_id),
    KEY idx_dish_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS dish_stock (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dish_id BIGINT NOT NULL,
    available_stock INT NOT NULL DEFAULT 0,
    locked_stock INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dish_stock_dish_id (dish_id),
    CONSTRAINT chk_dish_stock_available_non_negative CHECK (available_stock >= 0),
    CONSTRAINT chk_dish_stock_locked_non_negative CHECK (locked_stock >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS shopping_cart (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    dish_id BIGINT NOT NULL,
    dish_name VARCHAR(64) NOT NULL,
    dish_price DECIMAL(10, 2) NOT NULL,
    quantity INT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_cart_user_dish (user_id, dish_id),
    KEY idx_cart_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    number VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    status TINYINT NOT NULL COMMENT '1:待支付, 2:已支付, 3:已完成, 4:已取消',
    amount DECIMAL(10, 2) NOT NULL,
    remark VARCHAR(255),
    order_time DATETIME NOT NULL,
    pay_time DATETIME,
    cancel_time DATETIME,
    complete_time DATETIME,
    UNIQUE KEY uk_orders_number (number),
    KEY idx_orders_user_id (user_id),
    KEY idx_orders_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS order_detail (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    dish_id BIGINT NOT NULL,
    dish_name VARCHAR(64) NOT NULL,
    dish_price DECIMAL(10, 2) NOT NULL,
    quantity INT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    KEY idx_order_detail_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS stock_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dish_id BIGINT NOT NULL,
    order_id BIGINT,
    change_type VARCHAR(32) NOT NULL,
    change_quantity INT NOT NULL,
    available_before INT NOT NULL,
    available_after INT NOT NULL,
    locked_before INT NOT NULL,
    locked_after INT NOT NULL,
    operator_id BIGINT,
    remark VARCHAR(255),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_stock_record_dish_id (dish_id),
    KEY idx_stock_record_order_id (order_id),
    KEY idx_stock_record_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS order_idempotency (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    order_id BIGINT,
    status TINYINT NOT NULL COMMENT '1:处理中, 2:已成功, 3:已失败',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_idempotency_user_key (user_id, idempotency_key),
    KEY idx_order_idempotency_order_id (order_id),
    KEY idx_order_idempotency_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS payment_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    order_number VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    pay_channel VARCHAR(32) NOT NULL,
    trade_no VARCHAR(64) NOT NULL,
    third_trade_no VARCHAR(128),
    status TINYINT NOT NULL COMMENT '1:支付中, 2:支付成功, 3:支付失败, 4:已关闭',
    request_time DATETIME NOT NULL,
    success_time DATETIME,
    callback_time DATETIME,
    failure_reason VARCHAR(255),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_payment_trade_no (trade_no),
    KEY idx_payment_order_id (order_id),
    KEY idx_payment_user_id (user_id),
    KEY idx_payment_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
