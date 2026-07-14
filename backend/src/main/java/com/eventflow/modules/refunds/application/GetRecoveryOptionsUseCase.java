package com.eventflow.modules.refunds.application;

import com.eventflow.modules.refunds.application.result.RecoveryOptionsResult;
import com.eventflow.modules.ticketing.application.TicketingFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** GET /tickets/{id}/recovery-options: la opción determinista de recuperación (ADR-19). Solo lectura. */
@Service
public class GetRecoveryOptionsUseCase {

    private final TicketingFacade ticketing;

    public GetRecoveryOptionsUseCase(TicketingFacade ticketing) {
        this.ticketing = ticketing;
    }

    @Transactional(readOnly = true)
    public RecoveryOptionsResult execute(UUID ownerId, UUID ticketId) {
        TicketingFacade.TicketRecoveryView view = ticketing.recoveryView(ticketId, ownerId);
        return new RecoveryOptionsResult(view.ticketId(), view.recovery());
    }
}
