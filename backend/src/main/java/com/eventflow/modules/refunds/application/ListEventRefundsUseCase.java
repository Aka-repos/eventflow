package com.eventflow.modules.refunds.application;

import com.eventflow.modules.catalog.application.CatalogFacade;
import com.eventflow.modules.refunds.domain.RefundRequest;
import com.eventflow.modules.refunds.domain.RefundStatus;
import com.eventflow.modules.refunds.domain.exception.RefundNotFoundException;
import com.eventflow.modules.refunds.domain.port.RefundRequestRepository;
import com.eventflow.shared.web.CursorPage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** GET /organizer/events/{eventId}/refund-requests: bandeja del organizador dueño del evento. */
@Service
public class ListEventRefundsUseCase {

    private final RefundRequestRepository refundRepository;
    private final CatalogFacade catalog;

    public ListEventRefundsUseCase(RefundRequestRepository refundRepository, CatalogFacade catalog) {
        this.refundRepository = refundRepository;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public CursorPage<RefundRequest> execute(UUID organizerId, UUID eventId, RefundStatus status,
                                             String cursor, int limit) {
        if (!catalog.isEventOrganizer(eventId, organizerId)) {
            throw new RefundNotFoundException(); // 404 si el evento no es suyo
        }
        return refundRepository.findByEvent(eventId, status, cursor, limit);
    }
}
