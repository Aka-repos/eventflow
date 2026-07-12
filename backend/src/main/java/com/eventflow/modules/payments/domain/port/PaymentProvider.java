package com.eventflow.modules.payments.domain.port;

import com.eventflow.shared.domain.Money;

import java.util.Optional;
import java.util.UUID;

/** Puerto del proveedor de pagos (ADR-06). v1: FakePaymentProvider; Stripe/etc. sin cambiar el dominio. */
public interface PaymentProvider {

    ProviderResult authorize(UUID paymentId, UUID orderId, Money amount, String method);

    /**
     * Verdad del proveedor para la reconciliación (H1/H2): estado de una autorización por nuestro
     * paymentId (client reference). Empty = el proveedor jamás procesó ese intent ⇒ es seguro declinarlo.
     */
    Optional<ProviderResult> lookup(UUID paymentId);

    record ProviderResult(boolean approved, String providerRef, String failureReason) {

        public static ProviderResult approved(String providerRef) {
            return new ProviderResult(true, providerRef, null);
        }

        public static ProviderResult declined(String reason) {
            return new ProviderResult(false, null, reason);
        }
    }
}
