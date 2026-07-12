# Módulo `ledger`

**Responsabilidad:** partida doble append-only (ADR-14). Inmutable también a nivel físico (REVOKE UPDATE/DELETE, V8).

## Superficie pública
`LedgerFacade.recordPrimarySale(buyerId, organizerId, amount, orderId, eventId, details)` — propagación MANDATORY: solo se invoca dentro de la TX del caso de uso que registra la venta (S2: mismo commit que la emisión de boletos).

Asiento v1 de venta primaria: `SALE BUYER:<uuid> → ORGANIZER:<uuid>` por el subtotal del evento, fee 0 (la comisión de plataforma solo existe en Exchange, `exchange.fee_pct`). Consumidores de lectura (dashboards) llegan en M10.
