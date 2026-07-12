package com.eventflow.modules.ordering.application;

import com.eventflow.modules.ordering.application.result.OrderResult;
import com.eventflow.modules.ordering.domain.Order;
import com.eventflow.modules.ordering.domain.exception.OrderNotFoundException;
import com.eventflow.modules.ordering.domain.port.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class CancelOrderUseCase {

    private final OrderRepository orderRepository;
    private final OrderingSupport support;
    private final OrderQueryAssembler assembler;

    public CancelOrderUseCase(OrderRepository orderRepository, OrderingSupport support,
                              OrderQueryAssembler assembler) {
        this.orderRepository = orderRepository;
        this.support = support;
        this.assembler = assembler;
    }

    @Transactional
    public OrderResult execute(UUID buyerId, UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .filter(o -> o.isOwnedBy(buyerId))
                .orElseThrow(OrderNotFoundException::new);
        order.cancel();
        support.releaseInventory(order);
        support.publishOrderCancelled(order, "USER", buyerId);
        orderRepository.save(order);
        return new OrderResult(order, assembler.descriptions(order), Map.of(), null);
    }
}
