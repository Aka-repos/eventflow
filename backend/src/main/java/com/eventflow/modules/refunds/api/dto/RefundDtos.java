package com.eventflow.modules.refunds.api.dto;

import com.eventflow.shared.web.MoneyDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** DTOs espejo EXACTO de los schemas congelados (CreateRefundRequest, RefundResponse, etc.). */
public final class RefundDtos {

    private RefundDtos() {
    }

    public record CreateRefundRequest(@Size(max = 500) String reason) {
    }

    public record RejectRefundRequest(@NotBlank @Size(min = 1, max = 500) String reason) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RefundResponse(UUID id, UUID ticketId, MoneyDto amount, String status, String reason,
                                 Instant createdAt, Instant resolvedAt) {
    }

    // ===== RecoveryOptionsResponse (tag tickets) =====
    public record RefundQuoteDto(MoneyDto amount, Instant deadline) {
    }

    public record ExchangeQuoteDto(MoneyDto originalPrice, int depreciationPct, MoneyDto listPrice,
                                   Instant listingDeadline) {
    }

    public record RecoveryLinksDto(String action) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RecoveryOptionsResponse(UUID ticketId, String option, String reason,
                                          RefundQuoteDto refund, ExchangeQuoteDto exchange,
                                          RecoveryLinksDto links) {
    }
}
