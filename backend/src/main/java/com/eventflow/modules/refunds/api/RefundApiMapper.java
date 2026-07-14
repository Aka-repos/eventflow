package com.eventflow.modules.refunds.api;

import com.eventflow.modules.refunds.api.dto.RefundDtos.ExchangeQuoteDto;
import com.eventflow.modules.refunds.api.dto.RefundDtos.RecoveryLinksDto;
import com.eventflow.modules.refunds.api.dto.RefundDtos.RecoveryOptionsResponse;
import com.eventflow.modules.refunds.api.dto.RefundDtos.RefundQuoteDto;
import com.eventflow.modules.refunds.api.dto.RefundDtos.RefundResponse;
import com.eventflow.modules.refunds.application.result.RecoveryOptionsResult;
import com.eventflow.modules.refunds.domain.RefundRequest;
import com.eventflow.modules.ticketing.domain.RecoveryPolicy;
import com.eventflow.shared.web.MoneyDto;
import org.springframework.stereotype.Component;

@Component
class RefundApiMapper {

    RefundResponse toResponse(RefundRequest r) {
        return new RefundResponse(r.getId(), r.getTicketId(), MoneyDto.from(r.getAmount()),
                r.getStatus().name(), r.getReason(), r.getCreatedAt(), r.getResolvedAt());
    }

    RecoveryOptionsResponse toRecovery(RecoveryOptionsResult result) {
        RecoveryPolicy.Recovery rec = result.recovery();
        RefundQuoteDto refund = rec.option() == RecoveryPolicy.Option.REFUND
                ? new RefundQuoteDto(MoneyDto.from(rec.refundAmount()), rec.refundDeadline())
                : null;
        ExchangeQuoteDto exchange = rec.option() == RecoveryPolicy.Option.EXCHANGE
                ? new ExchangeQuoteDto(MoneyDto.from(rec.exchangeOriginalPrice()), rec.exchangeDepreciationPct(),
                        MoneyDto.from(rec.exchangeListPrice()), rec.exchangeListingDeadline())
                : null;
        // el link de acción apunta al endpoint que ejecuta la opción (exchange-listings operativo en M6)
        String action = switch (rec.option()) {
            case REFUND -> "/tickets/" + result.ticketId() + "/refund-requests";
            case EXCHANGE -> "/tickets/" + result.ticketId() + "/exchange-listings";
            case NONE -> null;
        };
        return new RecoveryOptionsResponse(result.ticketId(), rec.option().name(), rec.reason().name(),
                refund, exchange, new RecoveryLinksDto(action));
    }
}
