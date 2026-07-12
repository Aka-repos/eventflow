package com.eventflow.modules.ticketing.api;

import com.eventflow.modules.ticketing.api.dto.TicketTypeDtos.CreateTicketTypeRequest;
import com.eventflow.modules.ticketing.api.dto.TicketTypeDtos.TicketTypeDto;
import com.eventflow.modules.ticketing.application.CreateTicketTypeUseCase;
import com.eventflow.modules.ticketing.application.DeleteTicketTypeUseCase;
import com.eventflow.modules.ticketing.application.UpdateTicketTypeUseCase;
import com.eventflow.modules.ticketing.application.command.TicketTypeCommand;
import com.eventflow.modules.ticketing.application.result.TicketTypeResult;
import com.eventflow.modules.ticketing.domain.TicketType;
import com.eventflow.shared.security.AuthenticatedUser;
import com.eventflow.shared.web.DataResponse;
import com.eventflow.shared.web.MoneyDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Tarifas del organizador (createTicketType/updateTicketType/deleteTicketType).
 * Vive en ticketing (dueño de la tabla); valida propiedad del evento vía CatalogFacade (S², doc 10).
 */
@RestController
@RequestMapping("/organizer/events/{eventId}/ticket-types")
@PreAuthorize("hasRole('ORGANIZER')")
class TicketTypeController {

    private final CreateTicketTypeUseCase createTicketType;
    private final UpdateTicketTypeUseCase updateTicketType;
    private final DeleteTicketTypeUseCase deleteTicketType;
    private final Clock clock;

    TicketTypeController(CreateTicketTypeUseCase createTicketType, UpdateTicketTypeUseCase updateTicketType,
                         DeleteTicketTypeUseCase deleteTicketType, Clock clock) {
        this.createTicketType = createTicketType;
        this.updateTicketType = updateTicketType;
        this.deleteTicketType = deleteTicketType;
        this.clock = clock;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DataResponse<TicketTypeDto> createTicketType(@PathVariable UUID eventId,
                                                 @Valid @RequestBody CreateTicketTypeRequest request,
                                                 @AuthenticationPrincipal AuthenticatedUser user) {
        TicketTypeResult result = createTicketType.execute(toCommand(user.id(), eventId, request));
        return DataResponse.of(toDto(result));
    }

    @PatchMapping("/{id}")
    DataResponse<TicketTypeDto> updateTicketType(@PathVariable UUID eventId, @PathVariable UUID id,
                                                 @RequestHeader("If-Match") int ifMatch,
                                                 @Valid @RequestBody CreateTicketTypeRequest request,
                                                 @AuthenticationPrincipal AuthenticatedUser user) {
        TicketTypeResult result = updateTicketType.execute(id, ifMatch, toCommand(user.id(), eventId, request));
        return DataResponse.of(toDto(result));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteTicketType(@PathVariable UUID eventId, @PathVariable UUID id,
                          @AuthenticationPrincipal AuthenticatedUser user) {
        deleteTicketType.execute(user.id(), eventId, id);
    }

    private static TicketTypeCommand toCommand(UUID organizerId, UUID eventId, CreateTicketTypeRequest r) {
        return new TicketTypeCommand(organizerId, eventId, r.name(), r.description(), r.price().toMoney(),
                r.zoneId(), r.totalQuantity(), r.salesStartsAt(), r.salesEndsAt());
    }

    private TicketTypeDto toDto(TicketTypeResult result) {
        TicketType t = result.ticketType();
        Instant now = clock.instant();
        boolean available = t.getSoldQuantity() < t.getTotalQuantity()
                && (t.getSalesStartsAt() == null || !now.isBefore(t.getSalesStartsAt()))
                && (t.getSalesEndsAt() == null || now.isBefore(t.getSalesEndsAt()));
        return new TicketTypeDto(t.getId(), t.getName(), t.getDescription(), MoneyDto.from(t.getPrice()),
                result.zoneName(), available, t.getSalesEndsAt());
    }
}
