package com.eventflow.modules.ordering.infrastructure.scheduler;

import com.eventflow.modules.ordering.application.CompleteApprovedOrdersUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** H2: completa órdenes PENDING con pago APPROVED (caída post-autorización). Idempotente. */
@Component
class OrderReconciliationJob {

    private final CompleteApprovedOrdersUseCase completeApprovedOrders;

    OrderReconciliationJob(CompleteApprovedOrdersUseCase completeApprovedOrders) {
        this.completeApprovedOrders = completeApprovedOrders;
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    void run() {
        completeApprovedOrders.execute();
    }
}
