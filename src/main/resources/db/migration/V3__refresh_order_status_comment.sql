ALTER TABLE orders
    MODIFY COLUMN status TINYINT NOT NULL
    COMMENT '1:待支付, 2:已支付, 3:已完成, 4:已取消, 5:已接单, 6:配送中, 7:模拟退款中, 8:模拟已退款';
