package com.eventflow.modules.ordering.infrastructure.scheduler;

import com.eventflow.modules.ordering.application.ExpireOrdersUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Red de seguridad de expiración (ADR-10): /pay también materializa la expiración inline. */
@Component
class OrderExpirationJob {

    private final ExpireOrdersUseCase expireOrders;

    OrderExpirationJob(ExpireOrdersUseCase expireOrders) {
        this.expireOrders = expireOrders;
    }

    @Scheduled(fixedDelayString = "PT30S", initialDelayString = "PT30S")
    void run() {
        expireOrders.execute();
    }
}
