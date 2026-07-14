package com.eventflow.modules.ordering.domain.port;

import com.eventflow.modules.ordering.domain.Order;
import com.eventflow.modules.ordering.domain.OrderStatus;
import com.eventflow.shared.web.CursorPage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(UUID id);

    /** Lock pesimista de la fila de la orden: serializa pay/cancel/expire (orden canónico de locks). */
    Optional<Order> findByIdForUpdate(UUID id);

    Optional<Order> findByIdempotencyKey(UUID idempotencyKey);

    /** order_id que contiene un order_item dado (para localizar el pago de adquisición, C2). */
    Optional<UUID> findOrderIdByItemId(UUID orderItemId);

    CursorPage<Order> findByBuyer(UUID buyerId, OrderStatus status, String cursor, int limit);

    /**
     * PENDING vencidas SIN intent de pago abierto/liquidado, FOR UPDATE SKIP LOCKED
     * (ADR-10/M7; el guard de pagos evita cancelar una orden cuyo cobro está en vuelo — H1).
     */
    List<Order> lockExpiredPending(Instant now, int batchSize);

    /** PENDING creadas antes de cutoff (candidatas a reconciliación de pago aprobado, H2). */
    List<UUID> findPendingCreatedBefore(Instant cutoff, int limit);
}
