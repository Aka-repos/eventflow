package com.eventflow.shared.outbox;

import jakarta.persistence.EntityManager;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Escribe el envelope api/08 §1 en ops.outbox_events dentro de la TX activa del caso de uso. */
@Component
class JpaOutboxPublisher implements OutboxPublisher {

    private final EntityManager entityManager;
    private final Clock clock;

    JpaOutboxPublisher(EntityManager entityManager, Clock clock) {
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Override
    public void publish(String aggregateType, UUID aggregateId, String eventType, int eventVersion,
                        UUID actorUserId, Map<String, Object> data) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", eventType);
        payload.put("eventVersion", eventVersion);
        payload.put("aggregateType", aggregateType);
        payload.put("aggregateId", aggregateId.toString());
        payload.put("occurredAt", clock.instant().toString());
        payload.put("correlationId", MDC.get("correlationId"));
        payload.put("actor", actorUserId == null ? null : Map.of("userId", actorUserId.toString()));
        payload.put("data", data);
        entityManager.persist(new OutboxEventRecord(aggregateType, aggregateId, eventType, payload));
    }
}
