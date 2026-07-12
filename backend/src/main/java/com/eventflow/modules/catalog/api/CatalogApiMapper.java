package com.eventflow.modules.catalog.api;

import com.eventflow.modules.catalog.api.dto.CatalogDtos.CategoryDto;
import com.eventflow.modules.catalog.api.dto.CatalogDtos.EventDetailDto;
import com.eventflow.modules.catalog.api.dto.CatalogDtos.EventPolicyPublicDto;
import com.eventflow.modules.catalog.api.dto.CatalogDtos.EventPolicyResponseDto;
import com.eventflow.modules.catalog.api.dto.CatalogDtos.EventSummaryDto;
import com.eventflow.modules.catalog.api.dto.CatalogDtos.OrganizerDto;
import com.eventflow.modules.catalog.api.dto.CatalogDtos.SponsorDto;
import com.eventflow.modules.catalog.api.dto.CatalogDtos.TicketTypeDto;
import com.eventflow.modules.catalog.api.dto.CatalogDtos.ZoneDto;
import com.eventflow.modules.catalog.application.result.EventDetailResult;
import com.eventflow.modules.catalog.domain.Category;
import com.eventflow.modules.catalog.domain.Event;
import com.eventflow.modules.catalog.domain.EventPolicy;
import com.eventflow.modules.catalog.domain.EventZone;
import com.eventflow.modules.catalog.domain.Sponsor;
import com.eventflow.modules.catalog.domain.port.EventListItem;
import com.eventflow.modules.catalog.domain.port.TariffsReadPort.TariffView;
import com.eventflow.shared.web.MoneyDto;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
class CatalogApiMapper {

    private final Clock clock;

    CatalogApiMapper(Clock clock) {
        this.clock = clock;
    }

    EventSummaryDto toSummary(EventListItem item) {
        return new EventSummaryDto(item.id(), item.title(), item.venueName(), item.startsAt(), item.endsAt(),
                item.timezone(), item.status().name(), item.coverUrl(),
                new CategoryDto(item.categoryId(), item.categoryName(), item.categoryIcon()),
                MoneyDto.from(item.priceFrom()), item.isFavorite());
    }

    EventDetailDto toDetail(EventDetailResult result) {
        Event event = result.event();
        Instant now = clock.instant();
        List<TicketTypeDto> ticketTypes = result.tariffs().stream()
                .map(t -> toTicketTypeDto(t, now))
                .toList();
        MoneyDto priceFrom = result.tariffs().stream()
                .findFirst()
                .map(t -> MoneyDto.from(t.price()))
                .orElse(null);
        EventPolicy policy = result.policy();
        return new EventDetailDto(event.getId(), event.getTitle(), event.getVenueName(),
                event.getStartsAt(), event.getEndsAt(), event.getTimezone(), event.getStatus().name(),
                event.getCoverUrl(), toCategoryDto(result.category()), priceFrom, result.isFavorite(),
                event.getDescription(), event.getAddress(),
                event.getLatitude() == null ? null : event.getLatitude().doubleValue(),
                event.getLongitude() == null ? null : event.getLongitude().doubleValue(),
                new OrganizerDto(event.getOrganizerId(), result.organizerName()),
                ticketTypes,
                result.zones().stream().map(this::toZoneDto).toList(),
                List.of(),
                result.sponsors().stream().map(this::toSponsorDto).toList(),
                toPolicyPublic(policy), result.waitlistOpen());
    }

    TicketTypeDto toTicketTypeDto(TariffView tariff, Instant now) {
        return new TicketTypeDto(tariff.id(), tariff.name(), tariff.description(),
                MoneyDto.from(tariff.price()), tariff.zoneName(), tariff.isAvailable(now), tariff.salesEndsAt());
    }

    CategoryDto toCategoryDto(Category category) {
        return category == null ? null : new CategoryDto(category.getId(), category.getName(), category.getIcon());
    }

    ZoneDto toZoneDto(EventZone zone) {
        return new ZoneDto(zone.getId(), zone.getName(), zone.getCapacity());
    }

    SponsorDto toSponsorDto(Sponsor sponsor) {
        return new SponsorDto(sponsor.getId(), sponsor.getName(), sponsor.getLogoUrl(), sponsor.getWebsite());
    }

    EventPolicyPublicDto toPolicyPublic(EventPolicy policy) {
        return new EventPolicyPublicDto(policy.getRefundWindowEndsAt(), policy.isExchangeEnabled(),
                policy.getExchangeDepreciationPct(), policy.isWaitlistEnabled(),
                policy.getQrVisibilityHoursBefore());
    }

    EventPolicyResponseDto toPolicyResponse(EventPolicy policy) {
        return new EventPolicyResponseDto(policy.getEventId(), policy.getRefundWindowEndsAt(),
                policy.isExchangeEnabled(), policy.getExchangeDepreciationPct(),
                policy.getExchangeListingDeadline(), policy.isWaitlistEnabled(),
                policy.getWaitlistOfferMinutes(), policy.getTempReservationMinutes(),
                policy.getQrVisibilityHoursBefore(), policy.getQrExpirationMinutes(),
                policy.getCancellationPolicy(), policy.getExtraPolicies(), policy.getVersion());
    }
}
