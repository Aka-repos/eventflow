package com.eventflow.modules.ordering.application;

import com.eventflow.modules.ordering.application.result.OrderResult;
import com.eventflow.modules.ordering.domain.Order;
import com.eventflow.modules.ordering.domain.OrderStatus;
import com.eventflow.modules.ordering.domain.exception.OrderNotFoundException;
import com.eventflow.modules.ordering.domain.port.OrderRepository;
import com.eventflow.modules.payments.application.PaymentsFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class GetOrderUseCase {

    private final OrderRepository orderRepository;
    private final OrderQueryAssembler assembler;
    private final PaymentsFacade payments;

    public GetOrderUseCase(OrderRepository orderRepository, OrderQueryAssembler assembler,
                           PaymentsFacade payments) {
        this.orderRepository = orderRepository;
        this.assembler = assembler;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public OrderResult execute(UUID buyerId, UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.isOwnedBy(buyerId))
                .orElseThrow(OrderNotFoundException::new);
        var ticketIds = order.getStatus() == OrderStatus.PAID ? assembler.ticketIds(order) : Map.<UUID, java.util.List<UUID>>of();
        return new OrderResult(order, assembler.descriptions(order), ticketIds,
                payments.latestForOrder(orderId).orElse(null));
    }
}
