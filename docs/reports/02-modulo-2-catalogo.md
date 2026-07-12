# Informe Módulo 2 — Catálogo de Eventos

- **Fecha:** 2026-07-11
- **Alcance:** exclusivamente el catálogo (backend `catalog` + tarifas en `ticketing` + feature Android espejo), implementado contra el contrato OpenAPI congelado, en el orden obligatorio del blueprint (dominio → casos de uso → persistencia → API → Android → testing). Cero cambios a ADRs, dominio diseñado, modelo de datos, migraciones u OpenAPI.

---

## 1. Casos de uso implementados

**Backend — módulo `catalog` (21):**
`SearchEvents` (full-text tsvector + filtros categoría/fechas/geodistancia haversine + keyset cursor), `GetEventDetail`, `ListCategories`, `ListFavorites`, `AddFavorite`, `RemoveFavorite`, `CreateEvent` (crea la política con defaults V3 en la misma TX), `ListOrganizerEvents`, `UpdateEvent` (If-Match; publicado solo campos seguros; reprogramación emite evento), `DeleteEvent` (soft, solo DRAFT), `PublishEvent` (exige tarifas; emite `EventPublished` al outbox en la misma TX), `GetEventPolicy`, `UpdateEventPolicy` (PUT + If-Match, ADR-03), `CreateZone`, `DeleteZone` (bloqueada con tarifas), `CreateCategory`, `UpdateCategory`, `DeleteCategory` (bloqueada en uso), `CreateSponsor`, `UpdateSponsor`, `DeleteSponsor`.

**Backend — módulo `ticketing` (3):** `CreateTicketType`, `UpdateTicketType` (If-Match; con vendidos solo cambios seguros), `DeleteTicketType` (solo sin ventas).

**Android (6):** `SearchEvents`, `GetEventDetail`, `GetCategories`, `ObserveFavorites` (Room, offline), `RefreshFavorites` (drena cola offline + resincroniza), `ToggleFavorite` (optimista + cola offline durable — la única mutación encolable según api/09).

## 2. Clases creadas

**Backend (58 clases + 2 README):**
- `modules/catalog/domain`: `Event` (agregado con máquina de estados design/04 §3), `EventStatus`, `EventUpdate`, `EventPolicy`, `EventZone`, `Category`, `Sponsor`, `Favorite`; 10 excepciones tipadas 1:1 con ErrorCode; eventos `EventPublished`/`EventRescheduled`; 9 puertos (incl. `EventSearchPort` y `TariffsReadPort`).
- `modules/catalog/application`: 21 use cases + `CatalogValidations`, `EventDetailAssembler`, `CatalogFacade` (única superficie para otros módulos), commands/results.
- `modules/catalog/infrastructure/persistence`: 6 adapters JPA + `EventSearchJdbcAdapter` (SQL nativo: tsvector/GIN, haversine, keyset, priceFrom lateral, isFavorite) + `TariffsReadJdbcAdapter` (proyección solo-lectura sobre `ticketing.ticket_types`).
- `modules/catalog/api`: `CatalogController`, `FavoritesController`, `OrganizerEventController`, `AdminCatalogController` + `CatalogApiMapper` + DTOs espejo.
- `modules/ticketing`: `TicketType` (dominio) + 3 use cases + adapter JPA + `TicketTypeController` + DTOs propios.
- `modules/identity/application/IdentityFacade` (S¹: nombre del organizador).
- `shared`: `Cursors`/`CursorPage`/`CursorMetaDto`/`PageResponse`/`MoneyDto` (paginación api/07 + Money del contrato), `OutboxPublisher`/`JpaOutboxPublisher`/`OutboxEventRecord` (envelope api/08 §1, misma TX), `VersionConflictException` (+`conflictVersion`), `SemanticValidationException`; `GlobalExceptionHandler` ampliado (optimistic lock JPA, header If-Match ausente, path param malformado).

**Android (25 archivos):** modelos de dominio del catálogo, `CatalogRepository` + impl offline-aware, 6 use cases, `CatalogApi` Retrofit, DTOs espejo, Room v2 (`FavoriteEventEntity`, `PendingFavoriteOpEntity`, `CatalogDao`, migración 1→2), mappers con fallback `UNKNOWN`, y el feature UI completo (abajo).

## 3. Endpoints implementados (23 operationIds del OpenAPI congelado)

| Grupo | Operaciones |
|---|---|
| Público (tag catalog) | `listEvents` (q, categoryId, dateFrom/To, nearLat/nearLng/radiusKm, sort ±startsAt, cursor, limit) · `getEvent` · `listCategories` |
| Tag me | `listFavorites` · `addFavorite` · `removeFavorite` (idempotentes) |
| Tag organizer | `createEvent` · `listOrganizerEvents` · `updateEvent` 🔒 · `deleteEvent` · `publishEvent` · `getEventPolicy` · `updateEventPolicy` 🔒 · `createZone` · `deleteZone` · `createTicketType` · `updateTicketType` 🔒 · `deleteTicketType` |
| Tag admin | `createCategory` · `updateCategory` · `deleteCategory` · `createSponsor` · `updateSponsor` · `deleteSponsor` |

🔒 = If-Match/optimistic lock con extensión `conflictVersion` en el 409. Seguridad por rol (`ORGANIZER`/`ADMIN` vía `@PreAuthorize`), 404 anti-enumeración para recursos ajenos.

**OpenAPI:** sin cambios al YAML (se implementó exactamente). Códigos de error nuevos registrados **aditivamente** en `docs/api/02` §3, el flujo que prescribe engineering/07 §2: `event_not_draft`, `event_not_publishable`, `category_in_use`, `category_name_taken`, `zone_in_use`, `ticket_type_has_sales`.

## 4. Pantallas Android implementadas

- **Home real** (reemplaza el placeholder del Módulo 1): Scaffold con tabs **Explorar** y **Favoritos** + logout.
- **Explorar** (`CatalogListScreen`): búsqueda con debounce 350 ms, chips de categoría, tarjetas con precio-desde/estado/corazón de favorito optimista, paginación por cursor ("Cargar más"), estados vacío/offline/error con retry.
- **Detalle** (`EventDetailScreen`): título/venue/fechas con timezone, organizador, descripción, entradas con precio/zona/disponibilidad, políticas públicas (reembolso/exchange/waitlist/QR), sponsors, toggle de favorito.
- **Favoritos** (`FavoritesScreen`): fuente de verdad Room (funciona offline), indicador de sin-conexión, quitar favorito.
- Navegación: ruta tipada `event/{eventId}` + tabs; sesión revocada sigue redirigiendo a Login.

Todas con la tríada `UiState/UiEvent/UiEffect` y ViewModels con `StateFlow`+`Channel`.

## 5. Tests ejecutados y cobertura

| Suite | Resultado |
|---|---|
| Backend total (unit dominio+application, ArchUnit, ITs Testcontainers) | **99/99 verdes** |
| — `CatalogFlowIT` (PostgreSQL 17 real + Flyway V1–V9) | 18 escenarios: flujo completo organizador→publicación→público, favoritos idempotentes, If-Match/conflictVersion, outbox EventPublished/EventRescheduled, keyset + cursor inválido 400, matriz de roles 401/403/404 |
| — ArchUnit | 10 reglas: capas + **matriz módulo→módulo del doc 10** (catalog↛ticketing, ticketing solo fachada de catalog, identity no llama a nadie, sin ciclos) |
| Cobertura backend domain+application | **89.0% líneas / 70.7% ramas** (estándar ≥80% cumplido en líneas) |
| Android unit (fakes + Turbine + MockWebServer) | **32/32 verdes** (15 nuevos: mappers con UNKNOWN, repo con cola offline real contra MockWebServer, ViewModels: debounce, paginación, optimista+revert, favoritos offline) |
| Android build | `assembleDebug` OK, 0 warnings |
| **E2E HTTP contra Supabase real** | **16/16**: admin crea categoría → organizador crea/configura/publica → búsqueda pública con priceFrom → detalle → favoritos → 403 por rol → outbox verificado en BD → datos de prueba limpiados |
| Flyway | `Successfully validated 9 migrations` — **sin cambios pendientes** (el módulo no requirió migraciones nuevas) |

## 6. Riesgos encontrados

1. **Cobertura de ramas 70.7%** en domain+application (líneas sí cumple): las ramas faltantes son mayormente accessors del builder `EventUpdate` y ramas defensivas de mappers. Sin impacto funcional; conviene elevarla en el endurecimiento (módulo 11).
2. **`registerSale()` aún sin consumidor transaccional**: el invariante de cupo se ejercita por tests; el módulo 3 (ordering) deberá usarlo con `FOR UPDATE` según el orden canónico de locks.
3. **Búsqueda sin caché local**: la lista de eventos no se cachea en Room (solo favoritos); sin red, Explorar muestra estado offline con retry. Decisión consciente dentro de la matriz api/09; si se quiere browsing offline completo, proponerlo como mejora.
4. **Recorrido UI manual en emulador pendiente** (igual que el DoD del Módulo 0+1): la integración quedó verificada a nivel transporte (E2E HTTP) + parseo cliente (MockWebServer); falta el recorrido visual en el AVD Pixel_9.
5. **Repo aún sin `git init`**: dos módulos completos sin control de versiones es el mayor riesgo operativo del proyecto.

## 7. Mejoras detectadas (NO implementadas — requieren tu aprobación)

1. **Mapa en el detalle (Google Maps)**: el roadmap lo menciona para el módulo 2, pero exige API key y SDK propietario; se entregó lat/lng en el dominio listo para pintarlo. Propuesta: decidir proveedor (Google Maps vs OSM) antes de agregarlo.
2. **Vincular sponsors a eventos**: existe `sponsor_events` y el admin ya gestiona sponsors, pero el OpenAPI congelado no define endpoint de vinculación; hoy solo se puede poblar por SQL. Propuesta: operación aditiva `PUT /organizer/events/{id}/sponsors` en v1.
3. **Índice geográfico**: el filtro nearLat/nearLng usa haversine sobre el filtrado por estado (suficiente al volumen actual); con catálogo grande convendría PostGIS o earthdistance + índice GIST (migración aditiva).
4. **Paginación de `GET /me/favorites`**: el contrato la define sin cursor; con cientos de favoritos convendría paginarla (cambio aditivo de contrato).
5. **`search_vector` solo indexa título+descripción**: añadir `venue_name` mejoraría la búsqueda (requiere migración que regenere la columna).

## 8. Definition of Done

- ✅ Compilación backend y Android
- ✅ Tests en verde (99 backend + 32 Android)
- ✅ Flyway sin cambios pendientes (validate limpio contra Supabase)
- ✅ OpenAPI consistente (implementación exacta; errores registrados aditivamente en api/02)
- ✅ Backend funcionando (contra Supabase real)
- ✅ Android funcionando (APK debug, 0 warnings)
- ✅ Integración Android ↔ Backend verificada (E2E HTTP 16/16 + MockWebServer del cliente; recorrido visual en emulador pendiente como manual)
- ✅ Sin deuda técnica crítica (riesgos menores documentados en §6)
