package com.eventflow.modules.catalog.api.dto;

import com.eventflow.shared.web.MoneyDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** DTOs espejo EXACTO de components.schemas del OpenAPI congelado (tag catalog/organizer/me/admin). */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CategoryDto(int id, String name, String icon) {
    }

    public record SponsorDto(UUID id, String name, String logoUrl, String website) {
    }

    public record ZoneDto(UUID id, String name, int capacity) {
    }

    public record TicketTypeDto(UUID id, String name, String description, MoneyDto price,
                                String zoneName, boolean available, Instant salesEndsAt) {
    }

    public record EventPolicyPublicDto(Instant refundWindowEndsAt, boolean exchangeEnabled,
                                       int exchangeDepreciationPct, boolean waitlistEnabled,
                                       int qrVisibilityHoursBefore) {
    }

    /** isFavorite solo presente autenticado (schema: [boolean, null]). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EventSummaryDto(UUID id, String title, String venueName, Instant startsAt, Instant endsAt,
                                  String timezone, String status, String coverUrl, CategoryDto category,
                                  MoneyDto priceFrom, Boolean isFavorite) {
    }

    public record OrganizerDto(UUID id, String name) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EventDetailDto(UUID id, String title, String venueName, Instant startsAt, Instant endsAt,
                                 String timezone, String status, String coverUrl, CategoryDto category,
                                 MoneyDto priceFrom, Boolean isFavorite, String description, String address,
                                 Double latitude, Double longitude, OrganizerDto organizer,
                                 List<TicketTypeDto> ticketTypes, List<ZoneDto> zones, List<Object> parkings,
                                 List<SponsorDto> sponsors, EventPolicyPublicDto policies, boolean waitlistOpen) {
    }

    public record CreateEventRequest(
            @NotBlank @Size(min = 3, max = 200) String title,
            @NotNull String description,
            @NotNull Integer categoryId,
            @NotBlank String venueName,
            String address,
            @DecimalMin(value = "-90") @DecimalMax(value = "90") Double latitude,
            @DecimalMin(value = "-180") @DecimalMax(value = "180") Double longitude,
            @NotBlank String timezone,
            @NotNull Instant startsAt,
            @NotNull Instant endsAt) {
    }

    public record UpdateEventRequest(
            @Size(min = 3, max = 200) String title,
            String description,
            Integer categoryId,
            String venueName,
            String address,
            @DecimalMin(value = "-90") @DecimalMax(value = "90") Double latitude,
            @DecimalMin(value = "-180") @DecimalMax(value = "180") Double longitude,
            String timezone,
            Instant startsAt,
            Instant endsAt,
            String coverUrl) {
    }

    public record EventPolicyRequest(
            Instant refundWindowEndsAt,
            @NotNull Boolean exchangeEnabled,
            @NotNull @Min(0) @Max(100) Integer exchangeDepreciationPct,
            Instant exchangeListingDeadline,
            @NotNull Boolean waitlistEnabled,
            @NotNull @Min(1) Integer waitlistOfferMinutes,
            @NotNull @Min(1) Integer tempReservationMinutes,
            @NotNull @Min(0) Integer qrVisibilityHoursBefore,
            @NotNull @Min(1) Integer qrExpirationMinutes,
            String cancellationPolicy,
            Map<String, Object> extraPolicies) {
    }

    public record EventPolicyResponseDto(UUID eventId, Instant refundWindowEndsAt, boolean exchangeEnabled,
                                         int exchangeDepreciationPct, Instant exchangeListingDeadline,
                                         boolean waitlistEnabled, int waitlistOfferMinutes,
                                         int tempReservationMinutes, int qrVisibilityHoursBefore,
                                         int qrExpirationMinutes, String cancellationPolicy,
                                         Map<String, Object> extraPolicies, int version) {
    }

    public record CreateZoneRequest(@NotBlank @Size(max = 80) String name, @NotNull @Min(1) Integer capacity) {
    }

    public record CategoryRequest(@NotBlank @Size(max = 80) String name, String icon, @NotNull Boolean active) {
    }

    public record SponsorRequest(@NotBlank @Size(max = 120) String name, String logoUrl, String website) {
    }
}
