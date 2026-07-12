# Módulo `ordering`

**Responsabilidad:** órdenes de compra (S2): reserva de inventario al crear (vía `TicketingFacade.reserve`, FOR UPDATE), ventana de pago (`defaults.order_expiration_minutes`, 15 min), orquestación del pago, emisión de boletos, ledger y expiración.

## Eventos que emite (outbox, api/08)
`OrderCreated` · `PaymentConfirmed` · `PaymentFailed` · `OrderCancelled` (cause USER|EXPIRED) — todos v1, misma TX.

## Dependencias (doc 10, fila ordering)
→ `catalog` (S: snapshot de compra/política), `ticketing` (S: reserve/release/issue), `payments` (S: charge), `ledger` (S: asiento SALE). Solo fachadas de application (ArchUnit).

## Decisiones clave
- **Expiración en TX propia** (`OrderExpirer`): el 409 `order_expired` de /pay jamás revierte la cancelación+liberación ya commiteada. El scheduler (30 s, FOR UPDATE SKIP LOCKED, lote 50) es la red de seguridad.
- **402 después del commit** (`PayOrderUseCase` → `PayOrderProcessor`): el rechazo del proveedor deja Order FAILED + inventario liberado persistidos.
- **Idempotencia ADR-07** en createOrder/payOrder vía `shared.idempotency.IdempotencyService` + backstop físico `uq_orders_idem_key`.
- PARKING (M8) y EXCHANGE_TICKET (M6) se rechazan con 422 explicativo hasta su módulo.

## Excepción → ErrorCode
| Excepción | code | HTTP |
|---|---|---|
| OrderNotFoundException | `not_found` (anti-enumeración) | 404 |
| OrderExpiredException | `order_expired` | 409 |
| OrderNotPendingException | `order_not_pending` | 409 |
| EventSoldOutException / TariffSoldOut (ticketing) | `event_sold_out` | 409 |
| TariffSalesWindowClosed (ticketing) | `sales_window_closed` | 422 |
| PaymentFailedException | `payment_failed` | 402 |
