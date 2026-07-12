package com.eventflow.modules.ordering.api.dto;

import com.eventflow.shared.web.MoneyDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DTOs espejo EXACTO de components.schemas (tag orders). */
public final class OrderDtos {

    private OrderDtos() {
    }

    public record CreateOrderRequest(
            @NotEmpty @Size(max = 10) @Valid List<OrderItemRequest> items) {
    }

    public record OrderItemRequest(
            @NotBlank String type,
            @NotNull UUID referenceId,
            @NotNull @Min(1) @Max(10) Integer quantity) {
    }

    public record PayOrderRequest(@NotBlank String method) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OrderItemResponse(UUID id, String type, String description, int quantity,
                                    MoneyDto unitPrice, List<UUID> ticketIds) {
    }

    public record PaymentSummaryDto(UUID id, String provider, String status, MoneyDto amount) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OrderResponse(UUID id, String status, MoneyDto total, Instant expiresAt,
                                Instant createdAt, List<OrderItemResponse> items,
                                PaymentSummaryDto payment) {
    }
}
