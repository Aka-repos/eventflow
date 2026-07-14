# Módulo `checkin`

**Responsabilidad:** validación de acceso al evento (S7). Verifica firma JWS ES256, autoriza al escáner (organizador dueño o staff asignado), resuelve el QR bajo lock y registra el resultado. Toda validación es server-side: el cliente jamás decide si un QR es válido.

## Dependencias (doc 10, fila checkin)
→ `identity` (S⁷: staff + resolución de usuario, y persistencia de asignaciones), `catalog` (S: organizador dueño), `ticketing` (S: resolución+consumo atómico del QR). Solo fachadas de application. Nadie llama a checkin (consumidor terminal de la cadena de acceso).

## Casos de uso
- `EventCheckInUseCase`: firma → autorización → `TicketingFacade.resolveAndConsume` (lock del QR) → GRANTED registra + outbox `CheckInCompleted`; rechazo → `CheckInAuditor.recordDenial` (**TX propia REQUIRES_NEW**: sobrevive al rollback de la excepción) + outbox `CheckInDenied` + Problem.
- `AssignStaffUseCase` / `RemoveStaffUseCase`: gestión de staff (catalog autoriza, identity persiste).

## Excepción → ErrorCode
| Situación | code | HTTP |
|---|---|---|
| firma inválida/manipulada/alg cambiado | `qr_invalid` | 422 |
| token expirado | `qr_expired` | 422 |
| boleto ya usado / QR consumido | `already_used` | 409 |
| QR de otro evento | `checkin_wrong_event` | 422 |
| boleto no ACTIVE | `ticket_blocked` | 409 |
| escáner sin permiso | `staff_not_assigned` | 403 |

## Nota sobre `ops.event_checkins`
La FK compuesta `(ticket_id, event_id)` exige que el evento registrado sea el **real del boleto** (no el escaneado). Los tokens con firma inválida no identifican ningún QR real ⇒ no se registran aquí (van a auditoría general), coherente con `qr_id NOT NULL`.
