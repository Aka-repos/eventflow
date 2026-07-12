package com.eventflow.modules.payments.infrastructure.scheduler;

import com.eventflow.modules.payments.application.PaymentsFacade;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** H2: resuelve intents PENDING huérfanos con la verdad del proveedor (lookup). Idempotente. */
@Component
class PaymentReconciliationJob {

    private static final int BATCH_SIZE = 50;

    private final PaymentsFacade payments;

    PaymentReconciliationJob(PaymentsFacade payments) {
        this.payments = payments;
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    void run() {
        payments.reconcileStaleIntents(BATCH_SIZE);
    }
}
