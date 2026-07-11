# EventFlow — Visión y Decisiones Arquitectónicas

> Documento 0 de la serie de diseño. Leer antes que los demás.
> Serie: `00` visión · `01` actores y casos de uso · `02` secuencias · `03` arquitectura · `04` modelo de datos · `05` API · `06` roadmap

## 1. Visión

EventFlow es una plataforma de gestión integral de eventos cuyo diferenciador es que **la propiedad del boleto siempre la administra la plataforma**: los usuarios nunca intercambian códigos QR entre sí. El derecho de asistencia se transfiere únicamente mediante el **Official Ticket Exchange**, con QR dinámicos firmados, historial completo y trazabilidad total para el organizador.

## 2. Prioridad de reglas (orden vinculante)

Ante conflicto entre reglas, se respeta este orden:

1. **Seguridad**
2. **Integridad de los datos**
3. **Reglas del evento**
4. **Reglas del Official Ticket Exchange**
5. **Experiencia del usuario**

Ninguna decisión de diseño o implementación puede comprometer 1 o 2 para favorecer 3–5.

## 3. Decisiones arquitectónicas (ADR resumidas)

| # | Decisión | Alternativa descartada | Justificación |
|---|----------|------------------------|---------------|
| ADR-01 | **Monolito modular con arquitectura hexagonal** en Spring Boot (módulos: identity, catalog, ticketing, ordering, payments, exchange, waitlist, refunds, parking, checkin, notifications, analytics, audit) | Microservicios desde el día 1 | Un equipo pequeño no debe pagar el costo operativo de microservicios. Los módulos se comunican por interfaces y eventos de dominio, lo que permite extraerlos a microservicios después sin reescribir el dominio. |
| ADR-02 | **`EventPolicy` como agregado propio**, separado de `Event`, con columnas tipadas para políticas núcleo + columna `JSONB extra_policies` para reglas futuras | Columnas en la tabla `events` | Desacopla lo descriptivo de lo configurable (requisito explícito). El JSONB permite agregar políticas sin migrar la entidad principal. |
| ADR-03 | **Snapshot de políticas al momento de la compra** (`policy_snapshot` en el ticket/orden) | Leer siempre la política vigente | *Mejora propuesta.* Si el organizador cambia el período de reembolso después de la venta, los boletos ya vendidos conservan las condiciones bajo las que se compraron. Protege integridad y evita disputas. Las políticas *operativas* (ventana de visibilidad del QR, expiración del QR) sí se leen en vivo. |
| ADR-04 | **Máquinas de estado explícitas en el dominio** para Ticket, Order, ParkingSlot, ExchangeListing, WaitlistEntry, QR | Flags booleanos / updates libres de estado | Las transiciones inválidas se rechazan en el dominio (no en controllers ni ViewModels). Cada transición emite un evento de dominio auditable. |
| ADR-05 | **Value Object `Money` (BigDecimal + código de moneda ISO-4217)** en el dominio; `NUMERIC(12,2) + currency CHAR(3)` en BD; string decimal en la API (`"72.00"`) | `double`/`float`; enteros en centavos dispersos por el código | Elimina errores de redondeo en precios, depreciación y comisiones; el VO centraliza aritmética monetaria (sumar, aplicar %) y valida que no se mezclen monedas; preparado para multi-moneda. *(Mejora autorizada #12.)* |
| ADR-06 | **Bloqueo optimista (`@Version`) como base + bloqueo pesimista (`SELECT … FOR UPDATE`) en filas calientes** (compra de boleto, plaza de parking, cabeza de waitlist, validación de QR) | Solo optimista o solo pesimista | Optimista cubre ediciones concurrentes de organizadores; pesimista serializa la contención real (dos compradores del mismo boleto) sin reintentos visibles al usuario. |
| ADR-07 | **Idempotencia por `Idempotency-Key`** (header) + tabla `idempotency_keys` con respuesta persistida | Confiar en que el cliente no reintente | Compras, pagos, reembolsos, transferencias y check-ins pueden reintentarse por fallos de red móviles sin duplicar efectos. |
| ADR-08 | **QR = token opaco firmado (JWS, ES256) que solo contiene `qr_id`, `kid` y `exp`**; toda la verdad vive en el servidor | QR con datos del boleto/usuario | El QR no contiene información sensible; aunque se capture, el servidor valida estado, propietario, ventana de visibilidad y unicidad. Índice único parcial garantiza **un solo QR ACTIVO por boleto** a nivel de base de datos. |
| ADR-09 | **Patrón Outbox** para eventos de dominio → notificaciones y auditoría | Publicar notificaciones dentro de la transacción / llamadas directas | Garantiza que "pago confirmado" y "notificación enviada" no se desincronicen: el evento se guarda en la misma transacción y un dispatcher lo procesa después. Base para migrar a mensajería real (Kafka/SQS). |
| ADR-10 | **Expiraciones dirigidas por datos (`expires_at`) + scheduler de barrido** | Timers en memoria | Reservas temporales, ofertas de waitlist y publicaciones expiran por comparación transaccional contra `expires_at`; el scheduler solo "materializa" la expiración. Un timer en memoria se pierde al reiniciar el servidor. |
| ADR-11 | **`PaymentProvider` como puerto (interfaz)** con implementación `FakePaymentProvider` para el proyecto y adaptadores futuros (Stripe, PayPal, Yappy) | Integración directa con un proveedor | Requisito explícito de abstracción; permite demo sin cuentas reales. |
| ADR-12 | **Android offline-first: Room es la única fuente de verdad de la UI**; Retrofit sincroniza; el servidor siempre gana en conflictos de datos de negocio | UI leyendo directo de red | Cumple el requisito de funcionamiento parcial offline. Las operaciones de escritura críticas (comprar, publicar, check-in) **requieren** conexión: nunca se confía en estado local para decisiones de negocio (prioridad 1 y 2). |
| ADR-13 | **Rol `STAFF` (personal de acceso)** además de ADMIN/ORGANIZER/ATTENDEE | Que el organizador escanee todo | *Mejora propuesta.* En eventos reales el escaneo lo hace personal de puerta con permisos mínimos (solo validar QR de un evento asignado). Principio de menor privilegio. |
| ADR-14 | **Libro mayor (`ledger_entries`) para movimientos económicos**, estilo partida doble: cada asiento registra cuenta **origen**, cuenta **destino**, monto, tipo de movimiento, referencia (orden/transferencia), comisión y fecha; *append-only* | Calcular ingresos agregando tablas operativas | Cada movimiento económico queda como asiento inmutable; el dashboard y la conciliación se calculan del ledger, no de joins frágiles. El par origen/destino permite responder "¿de dónde salió y a dónde fue cada centavo?" (comprador → plataforma, plataforma → vendedor, plataforma → comprador en reembolso). *(Mejora autorizada #5.)* |
| ADR-15 | **UUID como clave primaria** en entidades expuestas por API | IDs autoincrementales expuestos | Evita enumeración de recursos (seguridad) y facilita sincronización offline y futura distribución. |
| ADR-16 | **Soft Delete en entidades críticas** (`deleted_at TIMESTAMPTZ NULL` en User, Event, Ticket, Order, Refund; **Payment quedó excluido** por la mejora aprobada en la auditoría — db/07-bd-07 §3.15: un pago es inmutable y su cancelación es un estado, no un borrado): los repositorios filtran `deleted_at IS NULL` por defecto y los índices únicos pasan a ser parciales (p. ej. `UNIQUE(email) WHERE deleted_at IS NULL`) | Borrado físico | La trazabilidad exigida por auditoría, ledger e historial de boletos se rompería con `DELETE` físico (FKs colgantes, reportes inconsistentes). Ticket, Payment y entradas de ledger/historial además son *append-only*: nunca se borran, ni siquiera lógicamente. *(Mejora autorizada #11.)* |
| ADR-17 | **Fechas en UTC**: `Instant`/`OffsetDateTime` en Java, `TIMESTAMPTZ` en PostgreSQL, ISO-8601 UTC en la API; la conversión a zona local es responsabilidad exclusiva de la capa de presentación (Android) | `LocalDateTime` / zona del servidor | Eventos, ventanas de reembolso y expiraciones dependen de instantes absolutos; mezclar zonas horarias en el dominio produce bugs de ventanas de tiempo (visibilidad de QR, deadlines). *(Mejora autorizada #13.)* |
| ADR-18 | **Catálogo formal de Domain Events** (nombres en pasado, payload versionado, publicados vía outbox): auditoría, notificaciones y estadísticas son *consumidores* de eventos, nunca llamadas inline desde los casos de uso | Llamadas directas a auditoría/notificaciones desde los services | Desacopla la lógica de negocio de sus efectos secundarios; agregar un consumidor nuevo (p. ej. métricas) no toca el dominio. Catálogo completo en doc 03 §2.4. *(Mejoras autorizadas #6 y #7.)* |
| ADR-19 | **El reembolso es un derecho exclusivo de la compra primaria.** Un boleto adquirido mediante el Official Ticket Exchange (`tickets.acquired_via = 'EXCHANGE'`) **nunca** puede solicitar reembolso, sin importar la ventana de reembolso del evento; su única vía de salida es re-publicarlo en el Exchange conforme a las políticas vigentes. El expediente de reembolso se autocontiene: `refund_requests` congela `amount = acquisition_price` (lo que pagó el propietario actual) y referencia el `payment_id` a devolver | Heredar la ventana de reembolso al comprador del Exchange | Decisión oficial de negocio (auditoría 08, M8/C2). Elimina el fraude "comprar con depreciación en el Exchange → reembolsar al precio original", simplifica el flujo de dinero (el organizador solo devuelve ventas primarias) y hace determinista la opción de recuperación que muestra la UI. |

## 4. Mejoras propuestas sobre el documento original

> **Estado: AUTORIZADAS.** El documento "Evolución del Diseño Arquitectónico" autoriza explícitamente las 16 mejoras. Mapa de cobertura: #1→ADR-03 · #2→ADR-13 · #3 y #4→§4.3–4.4 · #5→ADR-14 · #6→ADR-18 · #7→ADR-09 · #8→ADR-08/doc 04 §4 · #9→§4.8 · #10→ADR-15 · #11→ADR-16 · #12→ADR-05 · #13→ADR-17 · #14→ADR-06 · #15→ADR-07 · #16→ADR-04.

1. **Snapshot de políticas por boleto** (ADR-03): sin esto, cambiar una política reescribe retroactivamente contratos de compra ya celebrados.
2. **Rol STAFF** (ADR-13): separa "administrar el evento" de "validar acceso".
3. **`TicketType` (tipo de boleto/tarifa) entre Evento y Boleto**: el documento habla de "boletos" en general; en la práctica un evento vende tarifas (General, VIP, Early Bird) con precio, cupo y zona propios. El inventario vive en `TicketType`; cada `Ticket` es una instancia con identidad permanente.
4. **Orden con ítems polimórficos (`order_items.item_type = TICKET | PARKING | EXCHANGE_TICKET`)**: una sola ruta de pago para compra primaria, parking y exchange. El pago siempre referencia la orden (requisito), y el exchange reutiliza el mismo pipeline de pago en lugar de duplicarlo.
5. **Ledger económico** (ADR-14).
6. **Outbox + eventos de dominio** (ADR-09): la auditoría deja de ser "acordarse de escribir en la tabla audit" y pasa a ser un consumidor de los mismos eventos que disparan notificaciones.
7. **Doble candado contra reembolso+publicación simultáneos**: además de la regla en el dominio, índices únicos parciales en BD (`refund activo por ticket`, `listing activo por ticket`) y verificación cruzada en la misma transacción. La regla de negocio se vuelve imposible de violar incluso con bugs en la capa de aplicación.
8. **Versionado de API (`/api/v1`)** desde el inicio: barato hoy, carísimo después.

## 5. Política económica (invariantes)

- Reembolso aprobado ⇒ devolución del **100%**, comisión **0**, boleto vuelve al inventario oficial del evento.
- Venta en Exchange ⇒ precio = `precio_original × (1 − depreciación_evento)`; comisión EventFlow = `precio_exchange × comisión_global`; vendedor recibe el resto. La comisión solo se asienta cuando la transferencia se completa.
- El vendedor **nunca** fija ni modifica el precio. No existe sobreprecio.
- Comisión configurable desde `ConfiguracionGlobal` sin cambios de código; depreciación configurable por evento (rango validado, p. ej. 5–20%).

## 6. Alcance de este ciclo de diseño

Sin código hasta cerrar: actores y casos de uso (`01`), secuencias críticas (`02`), arquitectura y estructura de carpetas (`03`), modelo de datos (`04`), API y contratos (`05`), y plan por módulos (`06`).
