package com.eventflow.modules.ordering.application;

import com.eventflow.modules.ordering.domain.Order;
import com.eventflow.modules.ordering.domain.port.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Materializa expiraciones dirigidas por expires_at (ADR-10). Idempotente: FOR UPDATE SKIP LOCKED,
 * y una orden ya no-PENDING simplemente no aparece en el lote.
 */
@Service
public class ExpireOrdersUseCase {

    private static final int BATCH_SIZE = 50;
    private static final Logger log = LoggerFactory.getLogger(ExpireOrdersUseCase.class);

    private final OrderRepository orderRepository;
    private final OrderingSupport support;
    private final Clock clock;

    public ExpireOrdersUseCase(OrderRepository orderRepository, OrderingSupport support, Clock clock) {
        this.orderRepository = orderRepository;
        this.support = support;
        this.clock = clock;
    }

    @Transactional
    public int execute() {
        List<Order> expired = orderRepository.lockExpiredPending(clock.instant(), BATCH_SIZE);
        for (Order order : expired) {
            order.cancel();
            support.releaseInventory(order);
            support.publishOrderCancelled(order, "EXPIRED", null);
            orderRepository.save(order);
            log.info("order_expired orderId={}", order.getId());
        }
        return expired.size();
    }
}
