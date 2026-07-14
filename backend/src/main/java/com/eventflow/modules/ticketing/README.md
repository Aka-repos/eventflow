# Módulo `ticketing` (parcial — Módulo 2: solo tarifas)

**Responsabilidad actual:** CRUD de tarifas (`TicketType`, tabla `ticketing.ticket_types`) del organizador. El resto del contexto (boletos, QRs, historial) llega en los módulos 3–4.

## Reglas de dominio
- Sin ventas: edición libre. Con `sold_quantity > 0`: solo descripción, ventana de venta y **aumento** de cupo — precio/zona/nombre congelados (`ticket_type_has_sales` 409). DELETE solo sin ventas.
- `registerSale()` sustenta el invariante de cupo (lo consumirá ordering vía fachada en el módulo 3).

## Dependencias (doc 10)
- → `catalog.application.CatalogFacade` (S²): propiedad del evento (404 anti-enumeración) y validación/nombre de zona.

## Excepción → ErrorCode
| Excepción | code | HTTP |
|---|---|---|
| TicketTypeNotFoundException | `not_found` | 404 |
| TicketTypeHasSalesException | `ticket_type_has_sales` | 409 |
| (unique event_id+name en carrera) | `validation_error` | 422 |

## Ampliación Módulo 4 (QR dinámicos)
- `DynamicQr` (agregado): id = qr_id del JWS; el token firmado (ES256, `JwtQrSigner`) lleva solo qr_id/kid/exp (ADR-08). Índice único parcial ⇒ un solo QR vivo por boleto.
- `QrIssuer`: emite/reutiliza el QR (rota antes de expirar); `invalidateLive` en transferencia/reembolso/reissue.
- `TicketingFacade.resolveAndConsume`: para checkin — bajo lock del QR, valida estado/evento y consuma atómicamente (Ticket→USED, QR→CONSUMED, historia CHECKIN).
- Firma: `QrSigner` (puerto) + `JwtQrSigner` (ES256) + `QrKeyConfig` (llaves de config o par efímero en dev). El QR jamás se persiste ni viaja en eventos de dominio.
