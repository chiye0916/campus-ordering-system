package demo3.demo3_068.service;

public interface OrderTimeoutOutboxService {

    void createPendingForOrder(Long orderId);
}
