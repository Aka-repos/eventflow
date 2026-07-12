package com.eventflow.modules.ordering.api;

import com.eventflow.modules.ordering.api.dto.OrderDtos.OrderItemResponse;
import com.eventflow.modules.ordering.api.dto.OrderDtos.OrderResponse;
import com.eventflow.modules.ordering.api.dto.OrderDtos.PaymentSummaryDto;
import com.eventflow.modules.ordering.application.result.OrderResult;
import com.eventflow.modules.payments.application.PaymentResult;
import com.eventflow.shared.web.MoneyDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class OrderApiMapper {

    OrderResponse toResponse(OrderResult result) {
        List<OrderItemResponse> items = result.order().getItems().stream()
                .map(item -> new OrderItemResponse(item.getId(), item.getItemType(),
                        result.descriptionsByItem().getOrDefault(item.getId(), ""),
                        item.getQuantity(), MoneyDto.from(item.getUnitPrice()),
                        result.ticketIdsByItem().get(item.getId())))
                .toList();
        PaymentResult payment = result.payment();
        return new OrderResponse(result.order().getId(), result.order().getStatus().name(),
                MoneyDto.from(result.order().getTotal()), result.order().getExpiresAt(),
                result.order().getCreatedAt(), items,
                payment == null ? null : new PaymentSummaryDto(payment.id(), payment.provider(),
                        payment.status(), MoneyDto.from(payment.amount())));
    }
}
