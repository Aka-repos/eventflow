package com.eventflow.modules.refunds.domain.port;

import com.eventflow.modules.refunds.domain.RefundRequest;
import com.eventflow.modules.refunds.domain.RefundStatus;
import com.eventflow.shared.web.CursorPage;

import java.util.Optional;
import java.util.UUID;

public interface RefundRequestRepository {

    RefundRequest save(RefundRequest refund);

    Optional<RefundRequest> findById(UUID id);

    /** Lock pesimista para approve/reject (serializa la resolución concurrente del mismo reembolso). */
    Optional<RefundRequest> findByIdForUpdate(UUID id);

    boolean existsActiveForTicket(UUID ticketId);

    /** Reembolsos de un evento (bandeja del organizador), keyset por created_at. */
    CursorPage<RefundRequest> findByEvent(UUID eventId, RefundStatus status, String cursor, int limit);
}
