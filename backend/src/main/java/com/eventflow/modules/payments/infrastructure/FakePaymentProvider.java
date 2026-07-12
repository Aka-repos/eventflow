package com.eventflow.modules.payments.infrastructure;

import com.eventflow.modules.payments.domain.port.PaymentProvider;
import com.eventflow.shared.domain.Money;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proveedor simulado (global_config payments.providers=[FAKE]). Determinista: aprueba todo salvo
 * montos terminados en ".13" (gancho documentado del flujo 402). Mantiene un registro en memoria
 * de lo procesado — el "ledger del proveedor" que la reconciliación consulta vía lookup().
 */
@Component
class FakePaymentProvider implements PaymentProvider {

    private final Map<UUID, ProviderResult> processed = new ConcurrentHashMap<>();

    @Override
    public ProviderResult authorize(UUID paymentId, UUID orderId, Money amount, String method) {
        ProviderResult result = amount.amount().toPlainString().endsWith(".13")
                ? ProviderResult.declined("La tarjeta fue rechazada por el emisor")
                : ProviderResult.approved("FAKE-" + paymentId);
        processed.put(paymentId, result);
        return result;
    }

    @Override
    public Optional<ProviderResult> lookup(UUID paymentId) {
        return Optional.ofNullable(processed.get(paymentId));
    }
}
