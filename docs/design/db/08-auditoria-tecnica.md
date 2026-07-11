# EventFlow BD — 8. Auditoría Técnica del Modelo de Datos (DBA Senior)

> Revisión adversarial previa a la generación de SQL. Regla aplicada: **nada se acepta por haber sido aprobado antes**. Escala: CRÍTICA (bloquea la generación de SQL) · ALTA (corregir antes del SQL) · MEDIA (corregir o documentar antes del módulo afectado) · BAJA (mejora oportunista).

## Resumen ejecutivo

| Severidad | Cantidad | Estado del modelo |
|---|---|---|
| CRÍTICA | 2 | **El modelo NO está listo para SQL sin corregirlas** |
| ALTA | 8 | corregir antes de las migraciones |
| MEDIA | 9 | corregir o documentar como decisión |
| BAJA | 7 | oportunistas |

Los dos hallazgos críticos son **defectos de dominio que el diseño anterior no puede representar**, no detalles de tipos: (C1) la Waitlist no puede ofrecer "derecho a comprar del inventario", solo boletos concretos; (C2) el monto de reembolso está indefinido para boletos adquiridos en el Exchange. Ambos contradicen parcialmente el diccionario ya aprobado — mejor ahora que con migraciones en producción.

---

## HALLAZGOS CRÍTICOS

### C1 — `waitlist_offers.ticket_id` no puede representar el flujo principal de la Waitlist

- **Problema.** El diccionario define `waitlist_offers.ticket_id FK→tickets NOT NULL`. Pero cuando un boleto se libera por **reembolso o cancelación**, ese ticket pasa a estado terminal (`REFUNDED`/`CANCELLED`) y **vuelve al inventario** (`sold_quantity − 1`): lo que se ofrece al primero de la fila NO es ese ticket (es inmutable y terminal), sino el **derecho a comprar un ticket nuevo del `ticket_type`**. Solo en el caso "publicación en Exchange" se ofrece un ticket concreto (vía su listing). El modelo aprobado obliga a apuntar a un ticket muerto o a mentir reutilizándolo.
- **Impacto.** El flujo FIFO de la Waitlist — funcionalidad diferenciadora — es irrepresentable en el caso más frecuente (reembolso). Implementarlo forzaría hacks (resucitar tickets terminales) que violan la máquina de estados y el historial.
- **Riesgo.** Corrupción semántica del ciclo de vida del boleto; migración destructiva futura.
- **Solución propuesta.** Oferta con **fuente polimórfica cerrada** (mismo patrón que `order_items`, I1):
  - `source_type TEXT CHECK IN ('INVENTORY','EXCHANGE')`
  - `ticket_type_id UUID NULL FK→ticket_types` (caso INVENTORY: reembolso/cancelación)
  - `listing_id UUID NULL FK→exchange_listings` (caso EXCHANGE: boleto concreto a precio de intercambio)
  - `CHECK` de exactamente-una según `source_type`.
  - Únicos parciales ajustados: `UNIQUE (listing_id) WHERE status='OFFERED'` y se mantiene `UNIQUE (entry_id) WHERE status='OFFERED'`. Para INVENTORY no hay unicidad por ticket (pueden liberarse N cupos del mismo tipo → N ofertas simultáneas a N usuarios distintos, cada una consume 1 de inventario al aceptarse).
  - La aceptación crea una Orden normal (`TICKET` o `EXCHANGE_TICKET`), reutilizando el pipeline de pago.
- **Recomendación final.** Adoptar obligatoriamente antes del SQL. Actualiza 07-bd-04.

### C2 — Monto de reembolso indefinido para boletos adquiridos vía Exchange

- **Problema.** La política dice "reembolso = 100% del dinero pagado". El modelo solo conserva `tickets.original_price` (venta primaria). Si el propietario actual compró en el Exchange (pagó `exchange_price` = p. ej. $72), un reembolso calculado sobre `original_price` ($80) **devuelve más de lo pagado**: pérdida directa de dinero y vector de fraude (comprar barato en Exchange → pedir reembolso al precio original).
- **Impacto.** Bug financiero explotable; además `refund_requests` no referencia qué pago devolver (la cadena ticket→source_order_item→order→payment apunta al pago del **primer** comprador, no del actual).
- **Riesgo.** CRÍTICO: dinero real + fraude. Prioridades #1 y #2 del proyecto.
- **Solución propuesta.**
  1. Nueva columna `tickets.acquisition_price NUMERIC(12,2) NOT NULL` (+ `acquisition_order_item_id UUID NOT NULL FK→order_items`): lo que pagó **el propietario actual** y con qué ítem/orden. En venta primaria = `original_price`/ítem primario; en cada transferencia se actualizan dentro de la TX de transferencia (junto con owner y QR).
  2. `refund_requests` captura en el momento de la solicitud: `amount = acquisition_price` y `payment_id UUID NOT NULL FK→payments` (el pago APROBADO de la orden de adquisición). El expediente queda autocontenido.
  3. `source_order_item_id` se conserva (traza histórica de emisión); `acquisition_order_item_id` traza al dueño vigente.
- **Recomendación final.** Adoptar antes del SQL. Nota de dominio: si el organizador aprueba, al inventario vuelve el cupo y el asiento del ledger es `PLATFORM → BUYER:<actual>` por `acquisition_price`. Actualiza 07-bd-03/04 y el flujo S3.

---

## HALLAZGOS ALTOS

### A1 — Falta el estado de retención por Waitlist en `exchange_listings`
**Problema.** La regla "al publicar en el Exchange, primero se ofrece a la Waitlist" exige un listing ya creado (fija el precio con depreciación) pero **no visible ni comprable** mientras haya ofertas FIFO vivas. El CHECK aprobado (`PUBLISHED|RESERVED|SOLD|CANCELLED|EXPIRED`) no puede expresarlo; filtrarlo solo en app dejaría la regla de prioridad (#3 sobre #4) sin respaldo físico. **Solución.** Añadir estado `WAITLIST_HOLD` como estado inicial condicional (`WAITLIST_HOLD → PUBLISHED` cuando la fila se agota o nadie acepta; `WAITLIST_HOLD → SOLD` si un usuario de la fila compra). Incluirlo en el índice único parcial: `WHERE status IN ('WAITLIST_HOLD','PUBLISHED','RESERVED')`. **Recomendación.** Adoptar; actualiza máquina de estados del doc 04.

### A2 — Brecha de reconciliación de pagos (crash post-aprobación)
**Problema.** Secuencia S2: si el proveedor aprueba y el proceso muere antes del COMMIT local, el dinero se cobró y la BD dice `PENDING`. El modelo tiene `provider_ref` único pero ningún diseño de reconciliación. **Impacto.** Cobros huérfanos; disputa con clientes. **Solución.** (a) Crear `payments (PENDING, provider_ref)` **antes** de llamar al proveedor (patrón payment-intent); (b) job de reconciliación que consulta al proveedor los `PENDING` viejos y completa o revierte de forma idempotente; (c) el `UNIQUE (provider, provider_ref)` ya evita duplicados al reintentar. **Recomendación.** Documentar como parte del módulo 3; sin cambio de esquema (solo garantiza que el flujo escriba el intent primero).

### A3 — Deadlock real entre Transferencia y Check-in: falta orden canónico de locks
**Problema.** Transferencia bloquea `listing → ticket → qr`; check-in bloquea `qr → ticket`. Dos transacciones concurrentes sobre el mismo boleto pueden abrazarse (deadlock). PostgreSQL lo detecta y aborta una, pero en horas pico genera errores visibles y reintentos. **Solución.** Convención global de adquisición: **siempre `tickets` primero, luego hijos (`dynamic_qrs`), luego agregados vecinos (`exchange_listings`, `waitlist_*`) en orden alfabético de tabla + PK**. El check-in pasa a: resolver `qr_id` → `SELECT ticket FOR UPDATE` → `SELECT qr FOR UPDATE`. Documentar en 07-bd-06 §10 y hacerla obligatoria en code review. **Recomendación.** Adoptar; es una regla de implementación, no de esquema.

### A4 — Índice único de pagos permite re-cobrar una orden reembolsada
**Problema.** `UNIQUE (order_id) WHERE status='APPROVED'`: cuando un pago pasa a `REFUNDED`, la orden queda libre para un **segundo** APPROVED. **Solución.** `UNIQUE (order_id) WHERE status IN ('APPROVED','REFUNDED')`. **Recomendación.** Adoptar (cambio de una línea en el diccionario).

### A5 — Falta `events.timezone` (IANA)
**Problema.** Todo se guarda en UTC (correcto), pero un evento ocurre en la zona del **venue**, no en la del dispositivo: "24 h antes del evento", horarios impresos y notificaciones deben calcularse/mostrarse en hora del venue; un usuario viajando vería horas equivocadas. **Solución.** `events.timezone TEXT NOT NULL DEFAULT 'America/Panama'` (identificador IANA, validado en app). La API expone `startsAt` UTC + `timezone`; Android formatea. **Recomendación.** Adoptar.

### A6 — Columnas FK sin índice (PostgreSQL no indexa FKs automáticamente)
**Problema.** Varias FKs de consulta/join quedaron sin índice: `order_items(ticket_type_id)`, `order_items(parking_id)`, `staff_assignments(event_id)`, `ticket_transfers(listing_id)`, `waitlist_offers(entry_id)` (el parcial solo cubre OFFERED), `refund_requests(ticket_id)` (el parcial solo cubre REQUESTED; el historial del expediente consulta todas), `dynamic_qrs(ticket_id)` (el parcial solo cubre vigentes; "historial de QRs del boleto" escanea). **Impacto.** Seq scans en joins y en verificación de RESTRICT. **Solución.** Añadir índices B-tree simples sobre esas 7 columnas. **Recomendación.** Adoptar.

### A7 — `refund_requests` sin referencia al pago (consecuencia de C2)
Cubierto por C2: `payment_id NOT NULL` + `amount` congelado al solicitar. Se lista aparte porque también aplica a reembolsos de venta primaria (hoy la cadena hasta el pago son 3 joins que pueden ambiguarse con órdenes multi-ítem). **Recomendación.** Adoptar junto con C2.

### A8 — Supabase: pooler en modo transacción vs Hibernate
**Problema.** Supavisor en modo transacción (puerto 6543) degrada/rompe prepared statements de Hibernate y prohíbe estado de sesión. **Solución.** El backend (HikariCP) se conecta a la **conexión directa** (5432) o pooler en modo sesión; dimensionar pool ≤ límite del plan; el modo transacción queda solo para clientes serverless (no existen aquí). **Recomendación.** Documentar como requisito de configuración del módulo 0.

---

## HALLAZGOS MEDIOS

| # | Hallazgo | Solución / Recomendación |
|---|---|---|
| M1 | **Regla de redondeo de la comisión sin definir.** `fee = exchange_price × pct` produce fracciones de centavo; el CHECK `fee + seller = exchange_price` fallaría según cómo se redondee. | Definir: `fee = round_half_up(price × pct, 2)`; `seller_amount = exchange_price − fee` (el residuo siempre al vendedor). Documentar en el VO Money. |
| M2 | **`NUMERIC(12,2)` asume monedas de 2 decimales** (JPY=0, BHD=3). | Aceptable para MVP monomoneda; documentado como limitación consciente. Si llega multi-moneda real: `NUMERIC(12,4)` + escala por moneda en el VO. |
| M3 | **Semántica de `orders.status='REFUNDED'` con reembolsos parciales** (orden con 3 boletos, 1 reembolsado). | La orden permanece `PAID`; los reembolsos viven en `refund_requests` + ledger. `REFUNDED` solo si el 100% de los ítems se devuelve. Documentar; evaluar `PARTIALLY_REFUNDED` si el dashboard lo necesita. |
| M4 | **`event_checkins.event_id` desnormalizado sin garantía física** (a diferencia de `tickets.event_id` que tiene FK compuesta). | Añadir `UNIQUE (id, event_id)` en `tickets` y FK compuesta `event_checkins (ticket_id, event_id) → tickets (id, event_id)`. Mismo truco, consistencia física. |
| M5 | **Semántica de `sold_quantity` ambigua** (¿incluye órdenes PENDING?). | Definir: cuenta unidades **comprometidas** (PENDING no expiradas + PAID). El job de expiración devuelve cupo con lock en orden canónico (`orders → ticket_types`). Documentar. |
| M6 | **`UNIQUE (ticket_id) WHERE result='GRANTED'` prohíbe re-entrada** (eventos multi-día/festival con salida y regreso). | Consciente para el MVP (un ingreso por boleto, es la spec). Anotar que habilitar re-entrada exigirá migrar la constraint a política por evento (`allow_reentry` en policies). |
| M7 | **Dispatcher del outbox con una sola instancia implícita.** | Consumir con `SELECT … FOR UPDATE SKIP LOCKED LIMIT n` — habilita múltiples workers sin doble entrega y sin cambio de esquema. Adoptar como patrón desde el día 1. |
| M8 | **`policy_snapshot` heredado en transferencia: ¿qué derechos tiene el comprador del Exchange?** (¿ventana de reembolso del comprador original?) | Decisión de dominio pendiente. Propuesta: el snapshot se conserva pero el comprador de Exchange NO hereda ventana de reembolso (compró un remanente con depreciación); su vía de salida es re-publicar si la política lo permite. Requiere confirmación del negocio. |
| M9 | **Disponibilidad de parking por `COUNT` sobre `parking_slots`.** | Correcto hasta ~10³ plazas/parking. Si un mega-evento lo excede, contador con CHECK como en ticket_types. Umbral documentado. |

## HALLAZGOS BAJOS

| # | Hallazgo | Recomendación |
|---|---|---|
| B1 | `order_items ON DELETE CASCADE` contradice la filosofía RESTRICT-por-defecto (y chocaría con tickets→RESTRICT si algún día se ejercitara) | Cambiar a RESTRICT: uniformidad y cero sorpresas |
| B2 | `ticket_history.cause` TEXT libre | `CHECK IN (...)` con el catálogo de causas |
| B3 | Doble defensa de idempotencia (`orders.idempotency_key` + `ops.idempotency_keys`) | Mantener; documentar que la tabla ops es la autoridad y la columna es cinturón |
| B4 | `parking_reservations.expires_at` nullable sin condición | `CHECK (status NOT IN ('PENDING') OR expires_at IS NOT NULL)` |
| B5 | `users.phone` sin normalización E.164 | Validar en app; sin cambio de esquema |
| B6 | JPA: `@Where` está deprecado en Hibernate 6.3+ | Usar `@SQLRestriction("deleted_at IS NULL")`; mapear violaciones de únicos parciales a códigos de error del envelope (409) |
| B7 | Marketplace del Exchange consulta vía join tickets(event_id,status) | Suficiente con índices existentes; si el marketplace crece, desnormalizar `listings.event_id` con FK compuesta (patrón M4) |

---

## Revisión por área solicitada

1. **Dominio (agregados/VOs/ownership).** Límites transaccionales correctos y justificados (inventario fuera de Event, QR dentro de Ticket, Payment dentro de Order). Defectos C1/C2 eran de dominio y quedan resueltos arriba. Ownership: una columna = un dueño físico, correcto.
2. **Normalización.** 1FN–BCNF verificadas; 5 desnormalizaciones deliberadas con control físico o transaccional (07-bd-07 §1) + la nueva `acquisition_price` (C2), que es hecho histórico, no redundancia. Sin anomalías de actualización detectadas.
3. **PostgreSQL (tipos/constraints).** Correctos: UUID v7 app-side, BIGINT en append-only internas, NUMERIC para dinero, TIMESTAMPTZ, CITEXT, INET, TSVECTOR generado, TEXT+CHECK para enums. Ajustes: A4 (parcial de payments), A6 (índices FK), B4. `UNIQUE(id,event_id)` sobre ticket_types es intencional (requisito sintáctico de FK compuesta), extender a tickets (M4).
4. **Escalabilidad.** Mayor crecimiento: `audit_log`, `ledger_entries`, `event_checkins`, `notifications`, `outbox_events` (todas BIGINT + índices por fecha + partición futura definida). Hotspots: fila de `ticket_types` en on-sale masivo (lock corto, sin I/O externo dentro del lock — aceptado), cabeza de waitlist, `outbox PENDING` (índice parcial + SKIP LOCKED M7). Sin consultas N+1 estructurales; dashboard aislado en ledger/proyecciones.
5. **Integridad física.** Las 20 restricciones del 07-bd-06 §7 verificadas una a una; se añaden: A4 (doble cobro post-refund), M4 (checkin.event), C1 (unicidad de ofertas por listing), C2 (expediente de reembolso autocontenido). Con esto, **cada regla crítica del negocio tiene al menos una defensa física**, incluida la coherencia orden↔pago.
6. **Concurrencia.** Optimista/pesimista bien asignados; faltaba: **orden canónico de locks (A3)** y **SKIP LOCKED en outbox (M7)**. Aislamiento: READ COMMITTED + locks explícitos + updates condicionales (`WHERE status=:esperado`) es suficiente y compatible con el pooler; no se requiere SERIALIZABLE en ningún flujo (evita tormentas de reintentos).
7. **Auditoría.** Cadena completa: outbox→audit (imposible olvidar auditar), history/transfers/ledger/checkins append-only con `REVOKE`. Soft delete coherente (payments excluido, aprobado). Sin huecos detectados tras C2 (el expediente de reembolso queda autocontenido).
8. **Seguridad.** QR opaco firmado + validación server-side + único parcial: sin vector de duplicación. C2 cierra el fraude de reembolso-con-sobreprecio. `list_price ≤ original_price` cierra sobreprecio. UUID no enumerables; 404 para recursos ajenos (doc 05); JPA parametriza todo (sin inyección); RLS deny-by-default + rol de conexión sin DDL ni UPDATE/DELETE en append-only. JWT fuera del alcance de BD (revisado en doc 03).
9. **Performance.** Índices por consulta caliente completos tras A6; keyset pagination en todos los listados; covering indexes: no se justifican aún (los índices compuestos actuales ya cubren los filtros dominantes; añadir INCLUDE cuando haya EXPLAIN reales — no especular). Partición: diferida con criterio explícito (~10⁷ filas).
10. **Supabase/stack.** PG17 ✔ (nota: `uuidv7()` nativo llega en PG18 — la generación app-side ya lo resuelve). CITEXT/INET/GIN/tsvector `spanish` disponibles ✔. Flyway multi-schema: declarar `flyway.schemas=identity,catalog,ticketing,commerce,parking,ops` (crea los schemas y ancla la history table) ✔. Hibernate: `@Table(schema=…)`, IDENTITY para BIGINT, `@Version`, `@SQLRestriction` ✔. Conexión: directa/sesión, no pooler transaccional (A8).
11. **DDD/Clean/SOLID.** Schemas espejan bounded contexts; FKs cross-schema explícitas = dependencias visibles y acíclicas salvo el ciclo estructural tickets↔commerce (legal en PG, inherente al dominio: el comercio produce boletos y los boletos se recomercializan; documentado). Ninguna tabla sirve a dos agregados. Eventos de dominio como único canal de efectos secundarios: bajo acoplamiento real.
12. **¿Sobre-ingeniería para un MVP?** Se evaluó componente por componente: schemas por módulo (costo ~0, mantener), outbox (esencial para consistencia — mantener), ledger de partida doble (alternativa: single-entry con `account` único; ahorra poco y pierde conciliación — mantener), UUID v7 (un generador de 20 líneas — mantener), particionado (correctamente diferido), triggers (correctamente descartados). **Único recorte sugerido si el calendario aprieta:** posponer `parking_checkins.direction OUT` (la salida puede ser implícita al expirar el evento) y el `search_vector` (LIKE+trigram bastaría al inicio) — ambos reversibles sin migración destructiva. Veredicto: el diseño no tiene complejidad gratuita.

---

## Contradicciones con decisiones previas (transparencia)

| Decisión previa | Qué cambia esta auditoría | Por qué |
|---|---|---|
| `waitlist_offers.ticket_id NOT NULL` (07-bd-04) | Fuente polimórfica INVENTORY/EXCHANGE (C1) | el modelo aprobado no representa el caso principal |
| `refund = 100% de original_price` implícito | `acquisition_price` + `payment_id` en el expediente (C2/A7) | evita pérdida de dinero y fraude |
| Máquina de estados de listings (doc 04) | + `WAITLIST_HOLD` (A1) | prioridad Waitlist > Exchange necesita respaldo físico |
| `UNIQUE payments APPROVED` (07-bd-04) | incluye `REFUNDED` (A4) | cierra doble cobro post-reembolso |
| ADR-17 "todo UTC" | + `events.timezone` IANA (A5) | UTC para almacenar; la zona del venue es un dato del dominio, no de presentación |

## Plan de correcciones antes del SQL

1. Aplicar C1, C2, A1, A4, A5, A6, A7, M4, B1, B4 a los diccionarios (07-bd-03/04/05) y máquinas de estado (doc 04).
2. Documentar A2, A3, M1, M3, M5, M7, M8 como reglas de implementación en 07-bd-06.
3. Confirmación de negocio pendiente (una sola): **M8 — derechos del comprador de Exchange** (propuesta: sin ventana de reembolso heredada).

Con esas correcciones aplicadas, el modelo queda aprobado desde la perspectiva DBA para generar el esquema SQL y las migraciones Flyway.
