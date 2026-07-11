# EventFlow API — 07. Paginación, Filtros, Orden y Búsqueda

## 1. Estándar único: keyset/cursor

Toda colección potencialmente grande pagina por **cursor opaco** (keyset). No existe paginación por `page/offset` en v1 (rendimiento estable y sin duplicados/hoyos con inserciones concurrentes — decisión del diseño de BD, doc 07-bd-06).

### Request
```
GET /api/v1/events?cursor=eyJrIjpbIjIwMjYtMDgtMTVUMDI6MDA6MDBaIl0sImlkIjoiOWYyYyJ9&limit=20
```
- `limit`: 1–100, default 20.
- `cursor`: opaco para el cliente. Ausente = primera página.

### Response
```json
{ "data": [ … ],
  "meta": { "hasNext": true, "nextCursor": "eyJrIjpbIjIwMjYtMDgtMTZUMDA6MDA6MDBaIl0sImlkIjoiYWExMSJ9" } }
```
- `hasNext=false` ⇒ `nextCursor: null`. No hay cursor "previo" en v1 (los clientes acumulan — infinite scroll).

### Semántica del cursor (implementación servidor; el cliente NO la asume)
- Codifica base64url de `{k: [valores de las columnas de orden], id}` del último elemento.
- La query usa comparación de tupla `(col_orden, id) > (:k, :id)` sobre índice compuesto (keyset real, doc 07-bd-06 §8).
- El cursor incluye la dirección/orden con que fue emitido: **cambiar `sort` o filtros invalida el cursor** ⇒ `400 malformed_request` con `detail` explicativo; el cliente reinicia desde la primera página.
- Cursores no expiran, pero no garantizan resultados congelados (los datos siguen vivos).

## 2. Ordenamiento

- Query param `sort`; prefijo `-` = descendente: `sort=-startsAt`.
- **Lista blanca por endpoint** (documentada en OpenAPI como enum); valor fuera de lista ⇒ `400`.
- v1 admite **un** criterio de orden por request; el `id` como desempate lo agrega el servidor siempre (estabilidad).
- Defaults: eventos `startsAt` asc · órdenes/tickets/notificaciones `createdAt` desc · listings `publishedAt` desc.

## 3. Filtros

- Query params **tipados y explícitos** por endpoint (no gramática genérica de brackets — YAGNI para clientes propios):
  - Igualdad: `status=PAID`, `categoryId=3`.
  - Rangos de fecha: pares `dateFrom`/`dateTo` (ISO-8601 UTC, inclusivos).
  - Geo: `nearLat`+`nearLng`+`radiusKm` (default 25; requiere el par completo).
  - Flags: `unreadOnly=true`.
- Filtros se combinan con AND. Filtro sobre campo no soportado se **ignora** (tolerant reader) — no es error.
- Los filtros de estado usan exactamente los enums del contrato.

## 4. Búsqueda de texto

- `q=` en `/events` (título + descripción, español; respaldada por el índice GIN/tsvector) y `/admin/users` (nombre/email).
- Mínimo 2 caracteres (menos ⇒ se ignora); sin operadores especiales expuestos en v1.
- `q` compone con filtros y paginación normalmente; el orden con `q` es por relevancia (`sort` se ignora y se documenta así).

## 5. Reglas para el cliente Android

1. Trata `nextCursor` como caja negra; jamás lo parsea ni lo construye.
2. Persiste ítems en Room y usa el cursor solo para pedir más; el orden de UI lo da la fuente local.
3. Al cambiar filtros/orden/búsqueda: descarta cursor y lista, pide página 1.
4. `hasNext` gobierna el infinite scroll; nunca inferir fin por página incompleta.

## 6. Excepciones documentadas

- Colecciones pequeñas y acotadas devuelven array completo sin paginación: `/categories`, `/events/{id}/parkings`, `/me/favorites`, `/me/waitlist`, `/me/waitlist-offers`, `/admin/config`.
- `/admin/reports` pagina con el mismo estándar cuando el tipo de reporte es tabular.
