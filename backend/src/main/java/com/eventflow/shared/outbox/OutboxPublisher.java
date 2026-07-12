package com.eventflow.shared.outbox;

import java.util.Map;
import java.util.UUID;

/**
 * Publicación de domain events al outbox (ADR-09/18) en la MISMA transacción del caso de uso.
 * El payload sigue el envelope de api/08 §1; el dispatcher (módulo 9) los entrega at-least-once.
 */
public interface OutboxPublisher {

    void publish(String aggregateType, UUID aggregateId, String eventType, int eventVersion,
                 UUID actorUserId, Map<String, Object> data);
}
