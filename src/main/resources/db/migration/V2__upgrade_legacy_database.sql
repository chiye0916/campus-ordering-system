CREATE TABLE IF NOT EXISTS order_status_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    order_number VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    old_status TINYINT,
    new_status TINYINT NOT NULL,
    operation VARCHAR(64) NOT NULL,
    operator_id BIGINT,
    operator_role VARCHAR(32) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    trace_id VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_order_status_history_order_id_create_time_id (order_id, create_time, id),
    KEY idx_order_status_history_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS refund_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    refund_no VARCHAR(64) NOT NULL,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    order_number VARCHAR(64) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    reject_reason VARCHAR(255),
    reviewer_id BIGINT,
    review_time DATETIME,
    complete_time DATETIME,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_refund_request_order_id (order_id),
    UNIQUE KEY uk_refund_request_refund_no (refund_no),
    KEY idx_refund_request_user_id (user_id),
    KEY idx_refund_request_status (status),
    KEY idx_refund_request_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `user` (username, email, password, nickname, role)
SELECT 'system_timeout', NULL, '12345', '订单超时系统', 'SYSTEM'
WHERE NOT EXISTS (
    SELECT 1 FROM `user` WHERE username = 'system_timeout'
);
