# Informe Módulo 3 — Órdenes + Pagos + Ticketing (compra primaria)

- **Fecha:** 2026-07-12 (rev. 2, post-aprobaciones)
- **Estado: CERRADO — re-auditoría en verde.** Aprobaciones del usuario aplicadas: **H1 y H2 implementados y validados** (§0), **H3 implementado**, H4 aceptado como riesgo residual, H5/H6 ratificados como interpretaciones. Suite final: **138/138 backend · 41/41 Android · E2E Supabase con flujo trifásico verificado (0 intents huérfanos)**.

---

## 0. Cierre de H1/H2/H3 (rev. 2)

**H1 — Payment-intent trifásico, cobro fuera de locks:**
- `PaymentsFacade.charge` = fase 1 `createIntent` (TX propia + `pg_advisory_xact_lock` por orden: serializa intents concurrentes sin tocar filas ajenas) → fase 2 `authorize` **sin transacción** → fase 3 `resolve` (TX propia). `charge()` **verifica en runtime** que no exista TX activa (falla rápido si alguien reintroduce I/O bajo lock).
- `PayOrderUseCase` reestructurado: preflight de solo lectura (sin lock) → cobro → `OrderPaymentFinalizer` (TX-B bajo lock de la orden, idempotente — lo reutiliza la reconciliación).
- **Guard de expiración:** ni el scheduler (`NOT EXISTS` sobre intents PENDING/APPROVED/REFUNDED) ni `OrderExpirer` cancelan jamás una orden con cobro en vuelo o liquidado — la reconciliación decide.

**H2 — Reconciliación completa:**
- El puerto `PaymentProvider` ganó `lookup(paymentId)` (la verdad del proveedor); `FakePaymentProvider` mantiene su "ledger" en memoria.
- `PaymentsFacade.reconcileStaleIntents` (job PT1M, SKIP LOCKED): intents PENDING > 2 min → `lookup`; vacío ⇒ **DECLINED seguro** (el proveedor jamás cobró); registrado ⇒ estado real.
- `CompleteApprovedOrdersUseCase` (job PT1M): órdenes PENDING > 1 min con pago APPROVED → PAID + boletos + ledger + outbox vía el finalizador idempotente.

**Validación (`PaymentRecoveryIT`, Testcontainers, 5 escenarios — todos exigidos):**
| Escenario | Resultado |
|---|---|
| Caída entre creación y autorización | intent reconciliado a DECLINED; la orden queda protegida de expirar mientras el intent está abierto y se libera después ✅ |
| Caída después de autorización | intent reconciliado a APPROVED por la verdad del proveedor; la orden se completa con boletos+ledger+outbox y el inventario NO se libera ✅ |
| Reintentos idempotentes | reintento de pago sobre orden recuperada ⇒ 409 `order_not_pending`, exactamente 1 pago liquidado ✅ (replay por Idempotency-Key ya cubierto en OrderFlowIT) |
| Reconciliación idempotente | segunda pasada de ambos jobs ⇒ 0 cambios ✅ |
| Ausencia de doble cobro | pago concurrente con flujo trifásico ⇒ exactamente 1 APPROVED y 1 juego de boletos (advisory lock + uq_payments_order_settled) ✅ |

**H3 — Composición batch:** `OrderQueryAssembler` compone páginas con 3 consultas (tarifas batch, títulos batch, ticketIds batch) en lugar de N por ítem; `TicketingFacade.tariffSnapshots(ids)` y `CatalogFacade.eventTitles(ids)` nuevos.

**Limitación documentada del FAKE:** su "ledger" es memoria de proceso — un reinicio antes de reconciliar un intent autorizado lo declina (sin dinero real es inocuo; un proveedor real tiene ledger persistente, que es justo lo que el puerto modela). README de payments actualizado.

**Re-auditoría:** todos los puntos del §3 re-verificados con el flujo nuevo; ArchUnit 12/12 (la IT de recuperación vive en el paquete neutral `com.eventflow.recovery` por ser test de sistema transversal); E2E contra Supabase con build trifásico: compra PAID/APPROVED, **0 intents PENDING huérfanos** en la base. §4 queda histórico como registro de la auditoría original; §5-§6 actualizados abajo.

---

## 1. Qué se implementó (orden estricto del blueprint)

**Casos de uso backend (11):**
- `ordering`: CreateOrder (S2 parte 1: inventario FOR UPDATE → Order PENDING con TTL 15 min de `defaults.order_expiration_minutes` → outbox `OrderCreated`), PayOrder (S2 parte 2: cobro → PAID + emisión con snapshot + ledger + `PaymentConfirmed`, o FAILED + liberación + `PaymentFailed` con 402 lanzado tras el commit), CancelOrder (libera + `OrderCancelled` USER), ExpireOrders (scheduler 30 s, FOR UPDATE SKIP LOCKED, lote 50, idempotente) + `OrderExpirer` (expiración en TX propia — ver bug real §3), ListOrders/GetOrder (keyset).
- `ticketing` (ampliado): `TicketingFacade.reserve/release` (FOR UPDATE sobre `ticket_types`; ventana de venta + stock en el agregado), `issuePrimaryTickets` (Ticket ACTIVE con `policy_snapshot` ADR-03, `acquisition_price=original_price` C2, historia ISSUED append-only, `TicketPurchased` por boleto), ListMyTickets/GetTicketDetail (con `qrAvailableAt` y `canRecover` derivados del snapshot).
- `payments` (nuevo): `PaymentsFacade.charge` payment-intent en REQUIRES_NEW tras el puerto `PaymentProvider`; `FakePaymentProvider` (aprueba todo; total terminado en ".13" ⇒ rechazo determinista para demo/tests).
- `ledger` (nuevo): `LedgerFacade.recordPrimarySale` — asiento `SALE BUYER→ORGANIZER` por evento, misma TX del pago (MANDATORY), append-only físico.
- `shared`: `IdempotencyService` (ADR-07: reserva de clave en TX propia, replay del cuerpo cacheado, hash-mismatch ⇒ 422, concurrencia por espera de commit) + `PlatformConfig` (puerto de `ops.global_config`).

**Endpoints (7, espejo exacto del OpenAPI congelado):** `createOrder` ⚡, `payOrder` ⚡, `cancelOrder`, `listOrders`, `getOrder`, `listTickets`, `getTicket`. Cero cambios al YAML; cero migraciones Flyway nuevas.

**Android:** checkout completo (botón Comprar por tarifa en el detalle → resumen con stepper → orden con ventana de pago → pagar/cancelar → Boletos), tabs nuevas **Boletos** y **Órdenes** en el Home (lectura offline desde Room v3 conforme api/09 §3; **la compra jamás se encola offline**), `OrdersApi` con `Idempotency-Key` por intento, manejo del 402 con el motivo del proveedor.

**Domain events emitidos (api/08):** `OrderCreated`, `PaymentConfirmed`, `PaymentFailed`, `OrderCancelled(USER|EXPIRED)`, `TicketPurchased` — todos al outbox en la misma TX.

## 2. Resultados de verificación

| Suite | Resultado |
|---|---|
| Backend total | **138/138 verdes** (rev. 2: incluye `PaymentRecoveryIT` ×5 y unit del pago trifásico) |
| — `OrderFlowIT` (Testcontainers, 10 escenarios) | idempotencia (replay + reuse 422 + key required 422), sobreventa imposible, pago aprobado (boletos+ledger+outbox), rechazado (FAILED persistido + liberación), expiración materializada, cancelación, anti-enumeración, historial |
| — `OrderConcurrencyIT` (carreras reales, engineering/04 §5) | **12 compradores concurrentes sobre cupo 5 → exactamente 5 órdenes, sold=5**; **doble pago simultáneo → exactamente 1 liquidado, 1 juego de boletos, 1 asiento** |
| — ArchUnit | **12 reglas** (capas + matriz completa con ordering/payments/ledger + ciclos) |
| Cobertura domain+application | **91.5% líneas** (ordering 93.4 · ticketing 94.7 · payments 86.0 · ledger 78.6) / ramas 70.6% |
| Android | **41/41 verdes** (checkout happy/402/sold-out/cantidad congelada, historial offline, repo con MockWebServer y clave de idempotencia verificada) + APK debug |
| **E2E contra Supabase real** | **9/9**: login demo → orden 2×45.00 → replay idempotente → pago → 2 boletos con `qrAvailableAt` → historial → ledger/sold/outbox verificados en BD |
| Flyway | validate limpio (9 migraciones, sin pendientes) |

Bug real capturado por la IT durante el desarrollo (ya corregido y testeado): la materialización de expiración dentro de `/pay` se revertía con el rollback del 409 — se extrajo a `OrderExpirer` con TX propia (mismo patrón que la revocación de familia de tokens del M1).

## 3. Auditoría técnica — verificaciones en verde

| Área | Evidencia |
|---|---|
| Blueprint (orden dominio→…→Android, plantilla 07) | estructura de los 3 módulos nuevos espejo de la plantilla; READMEs con mapa excepción→ErrorCode |
| ADRs | 03 (snapshot en boleto, verificado en BD), 05 (Money/redondeo, montos string), 07 (idempotencia con scope por usuario), 10 (scheduler solo materializa `expires_at`), 14 (ledger append-only, REVOKE físico), 16 (soft delete en orders), 19 (`acquired_via` PRIMARY listo para el guard de M5) |
| Esquema PostgreSQL | 0 cambios; entidades espejo de V4/V5/V7; constraints ejercitados (uq_orders_idem_key, uq_payments_order_settled, ck_tickets_*) |
| OpenAPI / Android Contract | operationIds/DTOs exactos; matriz offline api/09 respetada (compra nunca encolada; boletos/órdenes legibles offline) |
| Dependencias prohibidas / acoplamiento | ArchUnit: ordering solo fachadas; payments/ledger no llaman a nadie; `PaymentResult` público evita fugar `payments.domain`; sin ciclos |
| Transacciones | `MANDATORY` en fachadas de escritura (misma TX del caso de uso), `REQUIRES_NEW` solo donde debe sobrevivir al rollback (charge, expirer implícito por TX separada, idempotencia) |
| Locks / carreras | orden canónico (tarifa → orden), SKIP LOCKED en scheduler, ITs de carrera en verde |
| Seguridad | recursos propios con 404 anti-enumeración; **ningún precio viene del cliente**; 401/403 verificados; errores RFC 9457 con `code` |
| MDC / correlación | `correlationId` en logs y en el envelope del outbox |

## 4. Hallazgos — REQUIEREN TU DECISIÓN (no corregidos)

### H1 · ALTA — El payment-intent no cumple A2 al 100% y el cobro ocurre dentro de transacción/lock
**Problema:** `PaymentsFacade.charge` persiste el intent PENDING y su resolución en **una sola** TX REQUIRES_NEW, con la llamada al proveedor en medio. Si el proceso muere durante `authorize`, la TX se revierte y **no queda intent huérfano que reconciliar** — exactamente lo que el patrón A2 (comentario de la tabla `payments`) quiere evitar. Además el cobro corre mientras la TX de la orden (suspendida) retiene el lock de la fila y una conexión del pool.
**Impacto:** con `FAKE` es nulo (latencia ~0, sin dinero real). Con un proveedor real: (a) cobro ejecutado sin registro interno ⇒ conciliación manual contra el proveedor; (b) locks/conexiones retenidos durante I/O externo ⇒ throughput degradado en on-sale.
**Alternativas:** (1) dividir `charge` en 3 fases — intent commit (REQUIRES_NEW) → `authorize` sin TX → resolución (REQUIRES_NEW) — y en `PayOrderProcessor` cobrar ANTES de tomar el lock de la orden, re-validando estado después; (2) mantener el diseño actual hasta M9 dado que el único proveedor es FAKE, dejando H1 como precondición bloqueante para integrar un proveedor real; (3) mover el pago a un flujo asíncrono (webhook/polling) — sobredimensionado para v1.
**Recomendación:** (1), ~medio día con sus ITs (incluye simular crash entre fases). Si prefieres velocidad de roadmap, (2) es defendible documentándolo como deuda bloqueante.

### H2 · MEDIA — No existe el job de reconciliación de intents PENDING huérfanos
Prescrito por el comentario de la tabla `payments`. Sin él, un intent PENDING permanente (post-H1) queda invisible. **Propuesta:** job `@Scheduled` (frecuencia 5 min) que marque DECLINED los PENDING > N min consultando al puerto (FAKE: siempre declinar); natural hacerlo junto con H1 o en M9. Depende de la decisión de H1.

### H3 · MEDIA — N+1 en la composición de OrderResponse
`descriptions()`/`ticketIds()`/`latestForOrder()` consultan por ítem/orden en `listOrders` (≤100 órdenes ⇒ ~300 queries en el peor caso) y parte de ello ocurre dentro de la TX de pago. **Propuesta:** proyección SQL batch en el assembler (sin cambio de contrato) — 2-3 h; alternativa mayor (persistir `description`) exigiría migración aditiva y no la recomiendo.

### H4 · BAJA — Clave de idempotencia atascada si el proceso muere entre reserva y respuesta
El replay devuelve 422 "operación en curso" hasta el TTL (48 h); el cliente debe usar clave nueva (la app genera clave por intento, así que en la práctica se auto-mitiga). **Propuesta:** el barrido de limpieza previsto por ADR-07 (aún no implementado) libere también claves sin respuesta > 15 min.

### H5 · BAJA (interpretación a ratificar) — Los QR no se crean en la compra
S2 menciona "QRs(PENDING_VISIBILITY)" en el pago, pero el ciclo de vida del QR (emisión JWS, ventana, invalidación) es del **Módulo 4** según el roadmap. Decisión tomada: diferir la creación de filas `dynamic_qrs` a M4 (emisión on-demand en `GET /tickets/{id}/qr`); `qrAvailableAt` ya se deriva del snapshot. Sin impacto de contrato en M3.

### H6 · BAJA (interpretación a ratificar) — El evento no transiciona a SOLD_OUT automáticamente
El guard por tarifa devuelve `event_sold_out` (contrato cumplido), pero `catalog.events.status` no cambia al agotarse todas las tarifas. La transición automática la necesita realmente la waitlist (M7, su orquestador). Ratificar que se difiere a M7.

### H7 · BAJA — Orden PENDING abandonada desde la app
Si el usuario sale del checkout sin pagar ni cancelar, el cupo queda retenido hasta la expiración (máx. 15 min; scheduler la libera). Aceptable por diseño; opcional cancelar al salir de la pantalla.

## 5. Riesgos residuales (producción / financieros / concurrencia / consistencia / rendimiento)

1. **Financiero:** H1+H2 cerrados (rev. 2). Restan: H4 (clave de idempotencia atascada hasta TTL si el proceso muere entre reserva y respuesta — aceptado como residual; auto-mitigado porque la app genera clave por intento) y la limitación del ledger en memoria del FAKE (§0), inocua sin dinero real.
2. **Consistencia:** emisión de boletos + ledger + outbox comparten TX con el PAID (atómico probado). El outbox aún no tiene dispatcher (M9) — los eventos se acumulan PENDING por diseño.
3. **Rendimiento:** H3; y el lock de tarifa en on-sale masivo es el hotspot aceptado por la auditoría de diseño (lock corto). El scheduler de expiración agrega carga mínima (índice parcial).
4. **Operativo:** el repo sigue **sin git init** — con cuatro módulos implementados es ya el riesgo dominante del proyecto.

## 6. Checklist Definition of Done

| Ítem | Estado |
|---|---|
| Compilación backend + Android | ✅ |
| Tests en verde | ✅ 132 backend / 41 Android |
| ArchUnit | ✅ 12 reglas (matriz completa) |
| Testcontainers | ✅ 4 ITs (contrato, concurrencia, seed, auth) |
| Integración Android (contrato + offline) | ✅ unit/MockWebServer; recorrido visual en emulador pendiente (igual que M1/M2) |
| Integración Supabase | ✅ E2E 9/9 (compra real verificada en BD) |
| Flyway sin cambios pendientes | ✅ |
| OpenAPI consistente | ✅ (sin modificaciones) |
| Cobertura ≥80% líneas | ✅ 91.5% (ledger 78.6% — 3 getters sin ejercitar; se cubre al cerrar H1/H3) |
| **Auditoría en verde** | ✅ **H1/H2/H3 cerrados y validados (rev. 2 §0); H4 residual aceptado; H5/H6 ratificados** |

## 7. Preparación para el Módulo 4 (QR dinámicos + Check-in)

Listo para arrancar cuando apruebes: `Ticket.policySnapshot` ya congela `qrVisibilityHoursBefore`/`qrExpirationMinutes`; `qrAvailableAt` calculado; `ticket_history` operativa; `shared.security` tiene la base JWS (JJWT); `staff_assignments` migrada desde V3. Decisión previa necesaria: H5 (dónde nacen las filas `dynamic_qrs`).
