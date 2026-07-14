package com.eventflow.modules.ticketing.api;

import com.eventflow.modules.ticketing.api.dto.TicketApiDtos.TicketResponse;
import com.eventflow.modules.ticketing.application.InvalidateTicketUseCase;
import com.eventflow.modules.ticketing.application.ReissueTicketUseCase;
import com.eventflow.modules.ticketing.domain.Ticket;
import com.eventflow.shared.idempotency.IdempotencyService;
import com.eventflow.shared.security.AuthenticatedUser;
import com.eventflow.shared.web.DataResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.UUID;

/** Tag organizer: invalidateTicket ⚡, reissueTicket ⚡ (dueño del evento del boleto). */
@RestController
@RequestMapping("/organizer/tickets")
@PreAuthorize("hasRole('ORGANIZER')")
class OrganizerTicketController {

    private final InvalidateTicketUseCase invalidateTicket;
    private final ReissueTicketUseCase reissueTicket;
    private final IdempotencyService idempotency;
    private final Clock clock;

    OrganizerTicketController(InvalidateTicketUseCase invalidateTicket, ReissueTicketUseCase reissueTicket,
                              IdempotencyService idempotency, Clock clock) {
        this.invalidateTicket = invalidateTicket;
        this.reissueTicket = reissueTicket;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    @PostMapping("/{ticketId}/invalidate")
    DataResponse<TicketResponse> invalidate(@PathVariable UUID ticketId,
                                            @RequestHeader(value = "Idempotency-Key", required = false) UUID key,
                                            @AuthenticationPrincipal AuthenticatedUser user) {
        TicketResponse response = idempotency.execute(user.id(), key, "invalidateTicket:" + ticketId, null,
                TicketResponse.class, () -> toResponse(invalidateTicket.execute(user.id(), ticketId)));
        return DataResponse.of(response);
    }

    @PostMapping("/{ticketId}/reissue")
    DataResponse<TicketResponse> reissue(@PathVariable UUID ticketId,
                                         @RequestHeader(value = "Idempotency-Key", required = false) UUID key,
                                         @AuthenticationPrincipal AuthenticatedUser user) {
        TicketResponse response = idempotency.execute(user.id(), key, "reissueTicket:" + ticketId, null,
                TicketResponse.class, () -> toResponse(reissueTicket.execute(user.id(), ticketId)));
        return DataResponse.of(response);
    }

    /** TicketResponse mínimo (el organizador no necesita el detalle completo del asistente). */
    private TicketResponse toResponse(Ticket ticket) {
        return new TicketResponse(ticket.getId(), null, null, null, ticket.getStatus().name(),
                ticket.getAcquiredVia().name(), ticket.getPurchasedAt(),
                ticket.qrAvailableAt(), ticket.canRecover(clock.instant()));
    }
}
