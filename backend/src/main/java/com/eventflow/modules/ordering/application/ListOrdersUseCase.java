package com.eventflow.modules.ordering.application;

import com.eventflow.modules.ordering.application.result.OrderResult;
import com.eventflow.modules.ordering.domain.Order;
import com.eventflow.modules.ordering.domain.OrderStatus;
import com.eventflow.modules.ordering.domain.port.OrderRepository;
import com.eventflow.modules.payments.application.PaymentsFacade;
import com.eventflow.shared.web.CursorPage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ListOrdersUseCase {

    private final OrderRepository orderRepository;
    private final OrderQueryAssembler assembler;
    private final PaymentsFacade payments;

    public ListOrdersUseCase(OrderRepository orderRepository, OrderQueryAssembler assembler,
                             PaymentsFacade payments) {
        this.orderRepository = orderRepository;
        this.assembler = assembler;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public CursorPage<OrderResult> execute(UUID buyerId, OrderStatus status, String cursor, int limit) {
        CursorPage<Order> page = orderRepository.findByBuyer(buyerId, status, cursor, limit);
        // H3: composición batch — 3 consultas por página en lugar de N por ítem
        Map<java.util.UUID, String> descriptions = assembler.descriptionsFor(page.items());
        Map<java.util.UUID, List<java.util.UUID>> ticketIds = assembler.ticketIdsFor(page.items());
        List<OrderResult> results = page.items().stream().map(order -> new OrderResult(order,
                descriptions,
                order.getStatus() == OrderStatus.PAID ? ticketIds : Map.of(),
                payments.latestForOrder(order.getId()).orElse(null))).toList();
        return new CursorPage<>(results, page.nextCursor());
    }
}
