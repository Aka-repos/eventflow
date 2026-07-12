package com.eventflow.modules.catalog.api;

import com.eventflow.modules.catalog.api.dto.CatalogDtos.CategoryDto;
import com.eventflow.modules.catalog.api.dto.CatalogDtos.EventDetailDto;
import com.eventflow.modules.catalog.api.dto.CatalogDtos.EventSummaryDto;
import com.eventflow.modules.catalog.application.GetEventDetailUseCase;
import com.eventflow.modules.catalog.application.ListCategoriesUseCase;
import com.eventflow.modules.catalog.application.SearchEventsUseCase;
import com.eventflow.modules.catalog.domain.port.EventListItem;
import com.eventflow.modules.catalog.domain.port.EventSearchQuery;
import com.eventflow.shared.error.SemanticValidationException;
import com.eventflow.shared.security.AuthenticatedUser;
import com.eventflow.shared.web.CursorPage;
import com.eventflow.shared.web.DataResponse;
import com.eventflow.shared.web.PageResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Tag catalog: operationIds listEvents, getEvent, listCategories (público; isFavorite si hay sesión). */
@RestController
class CatalogController {

    private final SearchEventsUseCase searchEvents;
    private final GetEventDetailUseCase getEventDetail;
    private final ListCategoriesUseCase listCategories;
    private final CatalogApiMapper mapper;

    CatalogController(SearchEventsUseCase searchEvents, GetEventDetailUseCase getEventDetail,
                      ListCategoriesUseCase listCategories, CatalogApiMapper mapper) {
        this.searchEvents = searchEvents;
        this.getEventDetail = getEventDetail;
        this.listCategories = listCategories;
        this.mapper = mapper;
    }

    @GetMapping("/events")
    PageResponse<EventSummaryDto> listEvents(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) Integer categoryId,
                                             @RequestParam(required = false) Instant dateFrom,
                                             @RequestParam(required = false) Instant dateTo,
                                             @RequestParam(required = false) Double nearLat,
                                             @RequestParam(required = false) Double nearLng,
                                             @RequestParam(required = false, defaultValue = "25") double radiusKm,
                                             @RequestParam(required = false, defaultValue = "startsAt") String sort,
                                             @RequestParam(required = false) String cursor,
                                             @RequestParam(required = false, defaultValue = "20") int limit,
                                             @AuthenticationPrincipal AuthenticatedUser viewer) {
        boolean descending = switch (sort) {
            case "startsAt" -> false;
            case "-startsAt" -> true;
            default -> throw new SemanticValidationException("sort", "sort admite startsAt o -startsAt");
        };
        if ((nearLat == null) != (nearLng == null)) {
            throw new SemanticValidationException("nearLat", "nearLat y nearLng deben venir en pareja");
        }
        int boundedLimit = Math.min(Math.max(limit, 1), 100);
        CursorPage<EventListItem> page = searchEvents.execute(new EventSearchQuery(q, categoryId,
                dateFrom, dateTo, nearLat, nearLng, radiusKm, descending, cursor, boundedLimit,
                viewer == null ? null : viewer.id()));
        return PageResponse.of(page.items().stream().map(mapper::toSummary).toList(), page);
    }

    @GetMapping("/events/{eventId}")
    DataResponse<EventDetailDto> getEvent(@PathVariable UUID eventId,
                                          @AuthenticationPrincipal AuthenticatedUser viewer) {
        return DataResponse.of(mapper.toDetail(
                getEventDetail.execute(eventId, viewer == null ? null : viewer.id())));
    }

    @GetMapping("/categories")
    DataResponse<List<CategoryDto>> listCategories() {
        return DataResponse.of(listCategories.execute().stream().map(mapper::toCategoryDto).toList());
    }
}
