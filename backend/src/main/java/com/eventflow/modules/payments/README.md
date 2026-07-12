# Módulo `payments`

**Responsabilidad:** intents de pago (patrón payment-intent, auditoría A2) tras el puerto `PaymentProvider` (ADR-06). v1: `FakePaymentProvider` (aprueba todo; montos terminados en ".13" se rechazan — gancho determinista para demo/tests del flujo 402).

## Superficie pública
`PaymentsFacade.charge(orderId, amount, method)` → `PaymentResult` (record público; nadie importa payments.domain) · `latestForOrder(orderId)`.

## Garantías (H1/H2 cerrados, patrón trifásico A2)
- **Fase 1** `createIntent`: TX propia + `pg_advisory_xact_lock` por orden (serializa intents concurrentes); el PENDING queda commiteado ANTES de tocar al proveedor.
- **Fase 2** `authorize`: sin transacción — cero locks/conexiones retenidos durante el I/O externo (`charge()` lo verifica en runtime y falla si hay TX activa).
- **Fase 3** `resolve`: TX propia.
- **Reconciliación** (`reconcileStaleIntents`, job PT1M, SKIP LOCKED): intents PENDING > 2 min se resuelven con la verdad del proveedor (`lookup`); lookup vacío ⇒ DECLINED seguro. La contraparte de ordering (`CompleteApprovedOrdersUseCase`) completa órdenes PENDING con pago APPROVED, y el guard de expiración jamás cancela una orden con intent abierto/liquidado.
- `uq_payments_order_settled`: doble cobro físicamente imposible (A4), probado bajo carrera.

## Limitación del FAKE (documentada)
El "ledger del proveedor" de `FakePaymentProvider` es memoria de proceso: un reinicio ANTES de reconciliar un intent autorizado hace que `lookup` devuelva vacío ⇒ DECLINED. Con FAKE no hay dinero real; un proveedor real tiene ledger persistente, que es justo lo que el puerto modela.
