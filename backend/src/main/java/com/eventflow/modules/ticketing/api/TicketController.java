package com.eventflow.modules.ticketing.api;

import com.eventflow.modules.catalog.application.CatalogFacade.EventCard;
import com.eventflow.modules.ticketing.api.dto.TicketApiDtos.CategoryLite;
import com.eventflow.modules.ticketing.api.dto.TicketApiDtos.EventSummaryLite;
import com.eventflow.modules.ticketing.api.dto.TicketApiDtos.TicketDetail;
import com.eventflow.modules.ticketing.api.dto.TicketApiDtos.TicketHistoryEntryDto;
import com.eventflow.modules.ticketing.api.dto.TicketApiDtos.QrResponse;
import com.eventflow.modules.ticketing.api.dto.TicketApiDtos.TicketResponse;
import com.eventflow.modules.ticketing.application.GetTicketDetailUseCase;
import com.eventflow.modules.ticketing.application.GetTicketQrUseCase;
import com.eventflow.modules.ticketing.application.QrIssuer;
import com.eventflow.modules.ticketing.application.ListMyTicketsUseCase;
import com.eventflow.modules.ticketing.application.result.TicketView;
import com.eventflow.modules.ticketing.domain.Ticket;
import com.eventflow.modules.ticketing.domain.TicketStatus;
import com.eventflow.shared.security.AuthenticatedUser;
import com.eventflow.shared.web.CursorPage;
import com.eventflow.shared.web.DataResponse;
import com.eventflow.shared.web.MoneyDto;
import com.eventflow.shared.web.PageResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.UUID;

/** Tag tickets: listTickets, getTicket (M3). QR/recovery llegan en M4/M5. */
@RestController
@RequestMapping("/tickets")
class TicketController {

    private final ListMyTicketsUseCase listTickets;
    private final GetTicketDetailUseCase getTicket;
    private final GetTicketQrUseCase getTicketQr;
    private final Clock clock;

    TicketController(ListMyTicketsUseCase listTickets, GetTicketDetailUseCase getTicket,
                     GetTicketQrUseCase getTicketQr, Clock clock) {
        this.listTickets = listTickets;
        this.getTicket = getTicket;
        this.getTicketQr = getTicketQr;
        this.clock = clock;
    }

    @GetMapping
    PageResponse<TicketResponse> listTickets(@RequestParam(required = false) TicketStatus status,
                                             @RequestParam(required = false) String cursor,
                                             @RequestParam(required = false, defaultValue = "20") int limit,
                                             @AuthenticationPrincipal AuthenticatedUser user) {
        int boundedLimit = Math.min(Math.max(limit, 1), 100);
        CursorPage<TicketView> page = listTickets.execute(user.id(), status, cursor, boundedLimit);
        return PageResponse.of(page.items().stream().map(this::toResponse).toList(), page);
    }

    @GetMapping("/{ticketId}")
    DataResponse<TicketDetail> getTicket(@PathVariable UUID ticketId,
                                         @AuthenticationPrincipal AuthenticatedUser user) {
        TicketView view = getTicket.execute(user.id(), ticketId);
        Ticket ticket = view.ticket();
        TicketResponse base = toResponse(view);
        return DataResponse.of(new TicketDetail(base.id(), base.event(), base.ticketTypeName(),
                base.zoneName(), base.status(), base.acquiredVia(), base.purchasedAt(),
                base.qrAvailableAt(), base.canRecover(),
                MoneyDto.from(ticket.getOriginalPrice()), MoneyDto.from(ticket.getAcquisitionPrice()),
                view.history().stream().map(h -> new TicketHistoryEntryDto(h.getFromStatus(),
                        h.getToStatus(), h.getCause(), h.getOccurredAt())).toList()));
    }

    @GetMapping("/{ticketId}/qr")
    DataResponse<QrResponse> getTicketQr(@PathVariable UUID ticketId,
                                         @AuthenticationPrincipal AuthenticatedUser user) {
        QrIssuer.IssuedQr qr = getTicketQr.execute(user.id(), ticketId);
        return DataResponse.of(new QrResponse(qr.token(), qr.expiresAt(), qr.refreshAfter()));
    }

    private TicketResponse toResponse(TicketView view) {
        Ticket ticket = view.ticket();
        EventCard card = view.event();
        EventSummaryLite event = card == null ? null : new EventSummaryLite(card.eventId(), card.title(),
                card.venueName(), card.startsAt(), card.endsAt(), card.timezone(), card.status(),
                card.coverUrl(), new CategoryLite(card.categoryId(), card.categoryName(), card.categoryIcon()));
        return new TicketResponse(ticket.getId(), event, view.ticketTypeName(), view.zoneName(),
                ticket.getStatus().name(), ticket.getAcquiredVia().name(), ticket.getPurchasedAt(),
                ticket.qrAvailableAt(), ticket.canRecover(clock.instant()));
    }
}
