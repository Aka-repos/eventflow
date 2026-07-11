# EventFlow API — 08. Domain Events Públicos (Outbox)

> Catálogo de los eventos de dominio (ADR-09/18) en su calidad de **contrato interno versionado**: los consumidores actuales son módulos propios (audit, notifications, analytics); el mismo contrato habilitará webhooks a organizadores en el futuro sin rediseño. Los eventos NO se exponen por HTTP en v1.

## 1. Envelope común (payload de `ops.outbox_events`)

```json
{
  "eventId": 184467,                    // BIGINT outbox — identidad para deduplicar
  "eventType": "TicketTransferred",
  "eventVersion": 1,
  "aggregateType": "Ticket",
  "aggregateId": "tk-91b2…",
  "occurredAt": "2026-07-09T14:46:02Z",
  "correlationId": "0b6c…",             // traza del request que lo originó
  "actor": { "userId": "u-…", "ip": "190.…", "device": "Android/1.0" },
  "data": { }                           // payload específico, esquema por eventType+eventVersion
}
```

## 2. Catálogo v1

| Evento | `data` (campos clave) | Consumidores | Notas |
|---|---|---|---|
| `OrderCreated` | orderId, buyerId, total, items[] | audit, analytics | — |
| `PaymentConfirmed` | orderId, paymentId, provider, amount | audit, notifications (compra exitosa), analytics | dispara emisión de boletos |
| `PaymentFailed` | orderId, paymentId, reason | audit, analytics | — |
| `OrderCancelled` | orderId, cause (USER\|EXPIRED) | audit, analytics | libera inventario |
| `TicketPurchased` | ticketId, ticketTypeId, eventId, ownerId, acquisitionPrice | audit, analytics | 1 por boleto emitido |
| `TicketPublished` | ticketId, listingId, listPrice, depreciationPct, held (bool = WAITLIST_HOLD) | audit, notifications, analytics | — |
| `TicketUnpublished` | ticketId, listingId, cause (SELLER_CANCELLED\|EXPIRED) | audit, notifications | QR nuevo emitido |
| `ListingReserved` | listingId, reservationId, buyerId, expiresAt | audit | — |
| `ListingExpired` | listingId, ticketId | audit, notifications (vendedor) | boleto vuelve al dueño |
| `TicketTransferred` | ticketId, listingId, fromOwnerId, toOwnerId, exchangePrice, feeAmount, sellerAmount | audit, notifications (ambas partes), analytics | asientos de ledger en la misma TX |
| `RefundRequested` | refundId, ticketId, requesterId, amount | audit, notifications (organizador) | — |
| `RefundApproved` / `RefundRejected` | refundId, ticketId, resolvedBy, amount? | audit, notifications (asistente), analytics | Approved emite `TicketReleased` |
| `TicketRefunded` | ticketId, refundId, amount | audit, analytics | — |
| `TicketReleased` | ticketId, eventId, ticketTypeId, cause (REFUND\|CANCELLATION\|EXCHANGE_PUBLISH) | **waitlist** (orquestador FIFO), audit | evento integrador (S5) |
| `WaitlistJoined` | entryId, eventId, userId, queueSeq | audit, analytics | — |
| `WaitlistOfferMade` | offerId, entryId, source (INVENTORY\|EXCHANGE), refId, expiresAt | notifications (push con countdown), audit | — |
| `WaitlistOfferExpired` | offerId, entryId | waitlist (siguiente en fila), audit | — |
| `WaitlistFulfilled` | entryId, orderId | audit, analytics | — |
| `QRCodeGenerated` / `QRCodeInvalidated` | qrId, subjectType, subjectId, cause | audit | jamás incluyen el token firmado |
| `ParkingReserved` | reservationId, parkingId, slotId, userId | notifications, audit | — |
| `ParkingOccupied` / `ParkingReleased` | reservationId, slotId | audit, analytics | — |
| `CheckInCompleted` | ticketId, eventId, scannedBy, result | audit, analytics (asistencia en vivo) | — |
| `CheckInDenied` | eventId, qrId?, denialCode, scannedBy | audit (antifraude) | — |
| `EventPublished` / `EventCancelled` / `EventRescheduled` | eventId, changes | notifications (asistentes del evento), audit | Rescheduled = cambio de horario |

## 3. Garantías de entrega y consumo

| Propiedad | Garantía |
|---|---|
| Publicación | misma transacción que el caso de uso (outbox — nunca se pierde ni se inventa) |
| Entrega | **at-least-once**: el dispatcher (`FOR UPDATE SKIP LOCKED`) reintenta con backoff (`attempts`, `last_error`) |
| Idempotencia del consumidor | obligatoria, deduplicando por `eventId` (los consumidores internos registran el último procesado; un webhook futuro exigirá dedupe del receptor) |
| Orden | garantizado **por agregado** (orden de `eventId` dentro del mismo `aggregateId`); no hay orden global entre agregados |
| Versionado | `eventVersion` entero; cambios de payload aditivos no lo suben, cambios estructurales sí y el consumidor soporta N y N−1 |
| Retención | los `PROCESSED` se conservan (fuente de re-proyección de analytics); partición futura junto a audit_log |

## 4. Futuro: webhooks a organizadores (fuera de alcance v1, diseño reservado)

- Suscripción por evento del catálogo §2 con firma HMAC-SHA256 (`X-EventFlow-Signature`), reintentos exponenciales 24 h y endpoint de replay.
- El envelope §1 ya es el cuerpo del webhook: adoptar webhooks no cambia productores ni esquema.
