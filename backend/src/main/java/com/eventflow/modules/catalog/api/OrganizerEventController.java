package com.eventflow.modules.catalog.api;

import com.eventflow.modules.catalog.api.dto.CatalogDtos.CreateEventRequest;
import com.eventflow.modules.catalog.api.dto.CatalogDtos.CreateZoneRequest;
import com.eventflow.modules.catalog.api.dto.CatalogDtos.EventDetailDto;
import com.eventflow.modules.catalog.api.dto.CatalogDtos.EventPolicyRequest;
import com.eventflow.modules.catalog.api.dto.CatalogDtos.EventPolicyResponseDto;
import com.eventflow.modules.catalog.api.dto.CatalogDtos.EventSummaryDto;
import com.eventflow.modules.catalog.api.dto.CatalogDtos.UpdateEventRequest;
import com.eventflow.modules.catalog.api.dto.CatalogDtos.ZoneDto;
import com.eventflow.modules.catalog.application.CreateEventUseCase;
import com.eventflow.modules.catalog.application.CreateZoneUseCase;
import com.eventflow.modules.catalog.application.DeleteEventUseCase;
import com.eventflow.modules.catalog.application.DeleteZoneUseCase;
import com.eventflow.modules.catalog.application.GetEventPolicyUseCase;
import com.eventflow.modules.catalog.application.ListOrganizerEventsUseCase;
import com.eventflow.modules.catalog.application.PublishEventUseCase;
import com.eventflow.modules.catalog.application.UpdateEventPolicyUseCase;
import com.eventflow.modules.catalog.application.UpdateEventUseCase;
import com.eventflow.modules.catalog.application.command.CreateEventCommand;
import com.eventflow.modules.catalog.application.command.UpdateEventCommand;
import com.eventflow.modules.catalog.application.command.UpdatePolicyCommand;
import com.eventflow.modules.catalog.application.result.EventDetailResult;
import com.eventflow.modules.catalog.domain.EventStatus;
import com.eventflow.modules.catalog.domain.EventUpdate;
import com.eventflow.modules.catalog.domain.EventZone;
import com.eventflow.modules.catalog.domain.port.EventListItem;
import com.eventflow.shared.security.AuthenticatedUser;
import com.eventflow.shared.web.CursorPage;
import com.eventflow.shared.web.DataResponse;
import com.eventflow.shared.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/** Tag organizer (eventos/política/zonas). operationIds espejo del OpenAPI congelado. */
@RestController
@RequestMapping("/organizer/events")
@PreAuthorize("hasRole('ORGANIZER')")
class OrganizerEventController {

    private final CreateEventUseCase createEvent;
    private final ListOrganizerEventsUseCase listEvents;
    private final UpdateEventUseCase updateEvent;
    private final DeleteEventUseCase deleteEvent;
    private final PublishEventUseCase publishEvent;
    private final GetEventPolicyUseCase getPolicy;
    private final UpdateEventPolicyUseCase updatePolicy;
    private final CreateZoneUseCase createZone;
    private final DeleteZoneUseCase deleteZone;
    private final CatalogApiMapper mapper;

    OrganizerEventController(CreateEventUseCase createEvent, ListOrganizerEventsUseCase listEvents,
                             UpdateEventUseCase updateEvent, DeleteEventUseCase deleteEvent,
                             PublishEventUseCase publishEvent, GetEventPolicyUseCase getPolicy,
                             UpdateEventPolicyUseCase updatePolicy, CreateZoneUseCase createZone,
                             DeleteZoneUseCase deleteZone, CatalogApiMapper mapper) {
        this.createEvent = createEvent;
        this.listEvents = listEvents;
        this.updateEvent = updateEvent;
        this.deleteEvent = deleteEvent;
        this.publishEvent = publishEvent;
        this.getPolicy = getPolicy;
        this.updatePolicy = updatePolicy;
        this.createZone = createZone;
        this.deleteZone = deleteZone;
        this.mapper = mapper;
    }

    @PostMapping
    ResponseEntity<DataResponse<EventDetailDto>> createEvent(@Valid @RequestBody CreateEventRequest request,
                                                             @AuthenticationPrincipal AuthenticatedUser user) {
        EventDetailResult result = createEvent.execute(new CreateEventCommand(user.id(), request.title(),
                request.description(), request.categoryId(), request.venueName(), request.address(),
                request.latitude(), request.longitude(), request.timezone(),
                request.startsAt(), request.endsAt()));
        return ResponseEntity.created(URI.create("/events/" + result.event().getId()))
                .body(DataResponse.of(mapper.toDetail(result)));
    }

    @GetMapping
    PageResponse<EventSummaryDto> listOrganizerEvents(@RequestParam(required = false) EventStatus status,
                                                      @RequestParam(required = false) String cursor,
                                                      @RequestParam(required = false, defaultValue = "20") int limit,
                                                      @AuthenticationPrincipal AuthenticatedUser user) {
        int boundedLimit = Math.min(Math.max(limit, 1), 100);
        CursorPage<EventListItem> page = listEvents.execute(user.id(), status, cursor, boundedLimit);
        return PageResponse.of(page.items().stream().map(mapper::toSummary).toList(), page);
    }

    @PatchMapping("/{eventId}")
    DataResponse<EventDetailDto> updateEvent(@PathVariable UUID eventId,
                                             @RequestHeader("If-Match") int ifMatch,
                                             @Valid @RequestBody UpdateEventRequest request,
                                             @AuthenticationPrincipal AuthenticatedUser user) {
        EventUpdate.Builder update = EventUpdate.builder();
        if (request.title() != null) {
            update.title(request.title());
        }
        if (request.description() != null) {
            update.description(request.description());
        }
        if (request.categoryId() != null) {
            update.categoryId(request.categoryId().shortValue());
        }
        if (request.venueName() != null) {
            update.venueName(request.venueName());
        }
        if (request.address() != null) {
            update.address(request.address());
        }
        if (request.latitude() != null || request.longitude() != null) {
            update.geo(request.latitude(), request.longitude());
        }
        if (request.timezone() != null) {
            update.timezone(request.timezone());
        }
        if (request.startsAt() != null) {
            update.startsAt(request.startsAt());
        }
        if (request.endsAt() != null) {
            update.endsAt(request.endsAt());
        }
        if (request.coverUrl() != null) {
            update.coverUrl(request.coverUrl());
        }
        EventDetailResult result = updateEvent.execute(new UpdateEventCommand(user.id(), eventId, ifMatch,
                update.build(), request.categoryId(), request.timezone()));
        return DataResponse.of(mapper.toDetail(result));
    }

    @DeleteMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteEvent(@PathVariable UUID eventId, @AuthenticationPrincipal AuthenticatedUser user) {
        deleteEvent.execute(user.id(), eventId);
    }

    @PostMapping("/{eventId}/publish")
    DataResponse<EventDetailDto> publishEvent(@PathVariable UUID eventId,
                                              @AuthenticationPrincipal AuthenticatedUser user) {
        return DataResponse.of(mapper.toDetail(publishEvent.execute(user.id(), eventId)));
    }

    @GetMapping("/{eventId}/policy")
    DataResponse<EventPolicyResponseDto> getEventPolicy(@PathVariable UUID eventId,
                                                        @AuthenticationPrincipal AuthenticatedUser user) {
        return DataResponse.of(mapper.toPolicyResponse(getPolicy.execute(user.id(), eventId)));
    }

    @PutMapping("/{eventId}/policy")
    DataResponse<EventPolicyResponseDto> updateEventPolicy(@PathVariable UUID eventId,
                                                           @RequestHeader("If-Match") int ifMatch,
                                                           @Valid @RequestBody EventPolicyRequest request,
                                                           @AuthenticationPrincipal AuthenticatedUser user) {
        return DataResponse.of(mapper.toPolicyResponse(updatePolicy.execute(new UpdatePolicyCommand(
                user.id(), eventId, ifMatch, request.refundWindowEndsAt(), request.exchangeEnabled(),
                request.exchangeDepreciationPct(), request.exchangeListingDeadline(),
                request.waitlistEnabled(), request.waitlistOfferMinutes(), request.tempReservationMinutes(),
                request.qrVisibilityHoursBefore(), request.qrExpirationMinutes(),
                request.cancellationPolicy(), request.extraPolicies()))));
    }

    @PostMapping("/{eventId}/zones")
    @ResponseStatus(HttpStatus.CREATED)
    DataResponse<ZoneDto> createZone(@PathVariable UUID eventId,
                                     @Valid @RequestBody CreateZoneRequest request,
                                     @AuthenticationPrincipal AuthenticatedUser user) {
        EventZone zone = createZone.execute(user.id(), eventId, request.name(), request.capacity());
        return DataResponse.of(mapper.toZoneDto(zone));
    }

    @DeleteMapping("/{eventId}/zones/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteZone(@PathVariable UUID eventId, @PathVariable UUID id,
                    @AuthenticationPrincipal AuthenticatedUser user) {
        deleteZone.execute(user.id(), eventId, id);
    }
}
