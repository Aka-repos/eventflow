package com.eventflow.modules.ticketing.api.dto;

import com.eventflow.shared.web.MoneyDto;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DTOs espejo de TicketResponse/TicketDetail (doc api/03 §5). */
public final class TicketApiDtos {

    private TicketApiDtos() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EventSummaryLite(UUID id, String title, String venueName, Instant startsAt, Instant endsAt,
                                   String timezone, String status, String coverUrl, CategoryLite category) {
    }

    public record CategoryLite(int id, String name, String icon) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TicketResponse(UUID id, EventSummaryLite event, String ticketTypeName, String zoneName,
                                 String status, String acquiredVia, Instant purchasedAt,
                                 Instant qrAvailableAt, boolean canRecover) {
    }

    public record TicketHistoryEntryDto(String fromStatus, String toStatus, String cause, Instant occurredAt) {
    }

    /** QrResponse (congelado): qrToken JWS opaco + expiración + cuándo re-pedirlo. */
    public record QrResponse(String qrToken, Instant expiresAt, Instant refreshAfter) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TicketDetail(UUID id, EventSummaryLite event, String ticketTypeName, String zoneName,
                               String status, String acquiredVia, Instant purchasedAt,
                               Instant qrAvailableAt, boolean canRecover, MoneyDto originalPrice,
                               MoneyDto acquisitionPrice, List<TicketHistoryEntryDto> history) {
    }
}
