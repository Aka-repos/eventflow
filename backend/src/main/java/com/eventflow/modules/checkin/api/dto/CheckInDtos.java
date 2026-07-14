package com.eventflow.modules.checkin.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/** DTOs espejo de CheckInRequest/CheckInResponse (OpenAPI congelado). */
public final class CheckInDtos {

    private CheckInDtos() {
    }

    public record CheckInRequest(@NotBlank String qrToken) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CheckInResponse(String result, String attendeeName, String ticketTypeName,
                                  String zoneName, String denialCode, Instant occurredAt) {
    }
}
