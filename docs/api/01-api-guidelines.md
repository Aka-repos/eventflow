# EventFlow API — 01. Guidelines Globales

> Contrato oficial Backend ↔ Android. Fuente de verdad ejecutable: [`05-openapi.yaml`](05-openapi.yaml). Este documento fija las convenciones que TODO endpoint respeta sin excepción. Supersede la sección de convenciones de `docs/design/05-api-rest.md`.

## 1. Base y versionado

- Base URL: `https://<host>/api/v1` — versionado por path (ADR del diseño). `v2` solo ante breaking change (ver [06-api-versioning.md](06-api-versioning.md)).
- Servers: `http://localhost:8080/api/v1` (dev) · `https://api.eventflow.app/api/v1` (prod, placeholder).

## 2. Nombres y rutas

- Recursos: **sustantivos, plural, kebab-case** (`/exchange-listings`, `/refund-requests`). Sin verbos en rutas.
- Sub-recursos para pertenencia (`/events/{eventId}/waitlist`); acciones no-CRUD como sub-recurso POST (`/orders/{orderId}/pay`, `/refund-requests/{id}/approve`).
- Prefijos por audiencia: público/asistente sin prefijo · `/organizer/**` · `/admin/**`. El prefijo NO sustituye la autorización por recurso (propiedad del boleto/evento se valida siempre en servidor).
- Path params en camelCase: `{eventId}`, `{ticketId}` — siempre UUID.

## 3. Métodos HTTP (RFC 9110)

| Método | Uso en EventFlow | Idempotente |
|---|---|---|
| GET | lectura; jamás muta estado | sí |
| POST | creación y acciones de negocio (`/pay`, `/approve`, `/check-ins`) | no — por eso exigen `Idempotency-Key` |
| PUT | reemplazo completo (perfil, política del evento) y asociaciones (`PUT /me/favorites/{eventId}`) | sí |
| PATCH | actualización parcial (evento en DRAFT) | no* |
| DELETE | remoción de asociación o cancelación de recurso propio (`DELETE /exchange-listings/{id}`) | sí |

## 4. Headers

| Header | Dirección | Obligatorio | Regla |
|---|---|---|---|
| `Authorization: Bearer <jwt>` | req | todos salvo `/auth/**` y catálogo público | access token 15 min; refresh vía `/auth/refresh` |
| `Content-Type: application/json` | req/res | en cuerpos | UTF-8; errores usan `application/problem+json` |
| `Idempotency-Key: <uuid>` | req | **obligatorio en los POST marcados ⚡** (compra, pago, reembolso, publicación, reserva, check-in, aceptar oferta) | UUID v4/v7 generado por el cliente; ver §7 |
| `X-Correlation-ID: <uuid>` | req/res | opcional en request; **siempre** en response | si el cliente no lo envía, el servidor lo genera; se propaga a logs y al `traceId` de los errores |
| `If-Match: <version>` | req | en PUT/PATCH de recursos con optimistic lock (evento, política, config) | versión entera del recurso; mismatch ⇒ `409 version_conflict` |
| `Location` | res | en todo `201 Created` | URL del recurso creado |
| `X-RateLimit-Limit / -Remaining / -Reset` y `Retry-After` | res | en endpoints limitados | ver §10 |
| `Deprecation`, `Sunset`, `Link rel="successor-version"` | res | en endpoints deprecados | ver doc 06 |

## 5. Formatos de datos

- **JSON camelCase**; enums en `UPPER_SNAKE_CASE` (espejo exacto de los CHECK de BD).
- **Fechas**: ISO-8601 UTC con sufijo `Z` (`2026-08-15T02:00:00Z`). La API nunca envía horas locales; los recursos de evento incluyen `timezone` (IANA) y el cliente formatea (ADR-17/A5).
- **Dinero**: objeto `Money = { "amount": "72.00", "currency": "USD" }` — `amount` es **string decimal** (nunca número JSON), `currency` ISO-4217. MVP: solo `USD` (Observación O5). El servidor calcula todos los precios; los requests **jamás** llevan montos (regla antifraude).
- **IDs**: UUID string. Los BIGINT internos (ledger, historial, auditoría) no se exponen.
- **Campos desconocidos**: el servidor los ignora en requests (tolerant reader); el cliente DEBE ignorar campos y valores enum desconocidos en responses (forward compatibility, doc 06).

## 6. Envelope de respuesta

- Éxito: `{ "data": <objeto|array>, "meta": { … } }` — `meta` solo cuando aporta (paginación).
- Error: **RFC 9457 `application/problem+json`** — sin envelope `data` (ver [02-api-error-model.md](02-api-error-model.md)).
- `204 No Content` sin cuerpo (DELETE, marcados como leído).

## 7. Idempotencia (⚡)

- El cliente genera un `Idempotency-Key` UUID **por intento de operación** (no por request HTTP): el reintento del mismo pago reutiliza la misma clave.
- Repetición con la misma clave y mismo cuerpo ⇒ el servidor devuelve la **respuesta original persistida** (mismo status/cuerpo) sin re-ejecutar.
- Misma clave con cuerpo distinto ⇒ `422 idempotency_key_reuse`.
- TTL 48 h. Alcance: por usuario autenticado.

## 8. Códigos HTTP (catálogo cerrado)

`200` lectura/acción OK · `201` creado + `Location` · `204` sin cuerpo · `400` request malformado (JSON inválido, tipos) · `401` token ausente/expirado/ inválido · `402` pago rechazado · `403` autenticado sin permiso (incl. `qr_not_yet_visible`) · `404` no existe **o no es tuyo** (anti-enumeración) · `409` conflicto de estado/concurrencia (`version_conflict`, `sold_out`, `listing_not_available`, `already_checked_in`…) · `422` semánticamente inválido (validación de campos, `idempotency_key_reuse`, reglas de negocio evaluables sin conflicto) · `429` rate limit + `Retry-After` · `500` error interno (sin detalles) · `503` mantenimiento + `Retry-After`.

Regla: **nunca** `200` con `"success": false`. El status HTTP es semántico.

## 9. Paginación, filtros, orden y búsqueda

Estándar único **keyset/cursor** definido en [07-pagination-filtering.md](07-pagination-filtering.md): `?cursor=<opaco>&limit=20`, respuesta con `meta.nextCursor`. Filtros como query params tipados y documentados por endpoint; orden con `sort=-startsAt` sobre lista blanca; búsqueda `q=`.

## 10. Rate limiting

| Grupo | Límite |
|---|---|
| `/auth/**` (por IP) | 10/min |
| Compra/pago/exchange (por usuario) | 20/min |
| Lecturas autenticadas (por usuario) | 300/min |
| Catálogo anónimo (por IP) | 60/min |

## 11. Seguridad del contrato

- OAuth2 Bearer JWT (access 15 min, refresh 14 días con rotación; reuso de refresh rotado revoca la familia).
- Los precios, montos, comisiones y depreciaciones **siempre** los calcula el servidor y viajan solo en responses.
- Recursos ajenos ⇒ `404` (no `403`).
- El QR viaja como `qrToken` opaco (JWS); el cliente nunca interpreta su contenido.
- Respuestas de error jamás exponen stack traces, SQL ni versiones.

## 12. Madurez REST (postura pragmática)

Richardson **Nivel 2 completo + hipermedia selectiva** (Nivel 3 donde aporta): `Location` en creaciones, `meta.nextCursor` en colecciones, `links` en `recovery-options` (la acción válida siguiente) y `type` URI en problems. No se adopta HATEOAS integral: Android navega por contrato tipado, no descubriendo enlaces (decisión registrada en Observaciones O6).

## Observaciones (inconsistencias contrato ↔ diseño previo)

| # | Problema | Impacto | Solución propuesta | ¿Dominio o contrato? |
|---|---|---|---|---|
| O1 | `docs/design/05-api-rest.md` definía errores con envelope propio `{error:{…}}`; esta fase exige RFC 9457 | doble estándar si no se resuelve | **RFC 9457 supersede al envelope**; el doc de diseño queda marcado como superseded | solo contrato |
| O2 | El diseño no fijaba header de correlación ni `If-Match` | trazabilidad y optimistic lock quedaban implícitos | formalizados en §4; `version` ya existe en BD | solo contrato |
| O3 | `POST /me/notifications/{id}/read` (diseño) no es idempotente-semántico | reintentos ambiguos | cambia a `PUT /me/notifications/{id}/read` (idempotente natural) | solo contrato |
| O4 | El marketplace debe excluir listings `WAITLIST_HOLD` (auditoría A1) | fuga de la prioridad waitlist si el contrato no lo fija | `GET /events/{id}/exchange-listings` documenta filtro servidor `status=PUBLISHED` únicamente | solo contrato |
| O5 | Multi-moneda: BD y DTOs la soportan, el MVP opera USD | clientes podrían asumir multi-moneda | contrato fija `currency` presente siempre, valor único `USD` en v1; ampliar es cambio aditivo | solo contrato |
| O6 | REST L3 estricto (HATEOAS) exigido vs cliente Android tipado | costo alto sin consumidor de hipermedia | hipermedia selectiva (§12) | solo contrato |

Ninguna observación requiere tocar dominio, BD ni ADRs.
