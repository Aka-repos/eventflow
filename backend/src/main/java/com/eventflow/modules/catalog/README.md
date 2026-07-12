# Módulo `catalog`

**Responsabilidad:** catálogo de eventos — agregado `Event` (máquina de estados design/04 §3), `EventPolicy` (ADR-02/03), `EventZone`, `Category`, `Sponsor` y favoritos del usuario. Búsqueda pública full-text (tsvector, índice GIN) con filtros, geodistancia (haversine) y paginación keyset por cursor opaco (api/07).

## Eventos que emite (outbox, api/08)
| Evento | Cuándo |
|---|---|
| `EventPublished` v1 | DRAFT→PUBLISHED (misma TX) |
| `EventRescheduled` v1 | cambio de horario de un evento publicado (misma TX) |

No consume eventos.

## Dependencias (doc 10)
- → `identity.application.IdentityFacade` (S¹: nombre del organizador para EventDetail).
- **Lectura SQL de `ticketing.ticket_types`** vía `TariffsReadPort` (proyección de solo lectura en su propia infraestructura). Justificación: la matriz prohíbe catalog→ticketing por fachada, pero `EventDetail.ticketTypes`, `priceFrom` y la regla de publish ("requiere tarifas") necesitan leerlas. No se importa código de ticketing (ArchUnit lo verifica) y catalog jamás escribe esas tablas.
- Expone `CatalogFacade` (consumida hoy por ticketing, S²).

## Excepción → ErrorCode
| Excepción | code | HTTP |
|---|---|---|
| EventNotFoundException | `not_found` (anti-enumeración: ajeno = 404) | 404 |
| EventNotDraftException | `event_not_draft` | 409 |
| EventNotPublishableException | `event_not_publishable` | 422 |
| EventFieldNotEditableException | `event_not_published` | 409 |
| CategoryNotFound/SponsorNotFound/ZoneNotFound | `not_found` | 404 |
| CategoryInUseException | `category_in_use` | 409 |
| CategoryNameTakenException | `category_name_taken` | 409 |
| ZoneInUseException | `zone_in_use` | 409 |
| shared.VersionConflictException | `version_conflict` (+`conflictVersion`) | 409 |

## Decisiones de implementación (dentro del contrato congelado)
- La política nace con defaults (espejo V3) al crear el evento ⇒ `GET policy` y `EventDetail.policies` siempre resuelven; publish exige solo tarifas.
- "Campos seguros" tras publicar: `description`, `coverUrl`, `address`, `startsAt/endsAt` (fechas ⇒ `EventRescheduled`). El resto ⇒ `event_not_published` 409.
- `GET /events` lista estados `PUBLISHED, SOLD_OUT, IN_PROGRESS`; `GET /events/{id}` responde cualquier estado salvo `DRAFT` (404).
- Zona duplicada en `createZone` ⇒ 422 (el contrato no declara 409 en esa operación).
- `EventDetail.parkings` = `[]` hasta el módulo 8; `waitlistOpen` se calcula (SOLD_OUT ∧ waitlistEnabled).
