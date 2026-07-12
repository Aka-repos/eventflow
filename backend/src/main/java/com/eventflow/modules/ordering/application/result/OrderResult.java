package com.eventflow.modules.ordering.application.result;

import com.eventflow.modules.ordering.domain.Order;
import com.eventflow.modules.payments.application.PaymentResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Orden + datos calculados para OrderResponse (descripciones, ticketIds, pago). */
public record OrderResult(Order order, Map<UUID, String> descriptionsByItem,
                          Map<UUID, List<UUID>> ticketIdsByItem, PaymentResult payment) {
}
