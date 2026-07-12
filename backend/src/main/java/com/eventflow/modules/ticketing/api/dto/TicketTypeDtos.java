package com.eventflow.modules.ticketing.api.dto;

import com.eventflow.shared.web.MoneyDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** DTOs espejo de los schemas TicketTypeDto/CreateTicketTypeRequest (OpenAPI congelado). */
public final class TicketTypeDtos {

    private TicketTypeDtos() {
    }

    public record TicketTypeDto(UUID id, String name, String description, MoneyDto price,
                                String zoneName, boolean available, Instant salesEndsAt) {
    }

    public record CreateTicketTypeRequest(
            @NotBlank @Size(max = 120) String name,
            String description,
            @NotNull @Valid MoneyDto price,
            UUID zoneId,
            @NotNull @Min(1) Integer totalQuantity,
            Instant salesStartsAt,
            Instant salesEndsAt) {
    }
}
