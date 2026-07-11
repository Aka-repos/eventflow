# EventFlow Engineering — 02. Guidelines Backend (Spring Boot)

## 1. Capas y responsabilidades (hexagonal por módulo)

```text
modules/<modulo>/
├── domain/          # Agregados, VOs, máquinas de estado, reglas, eventos de dominio, puertos de dominio
├── application/     # Casos de uso transaccionales, puertos de salida, fachada pública del módulo
├── infrastructure/  # Adaptadores: JPA repositories, clientes externos, scheduler handlers, consumidores outbox
└── api/             # Controllers + DTOs (records) + mappers DTO↔dominio
```

| Capa | MUST | MUST NOT |
|---|---|---|
| **Domain** | contener TODAS las invariantes y transiciones de estado; ser testeable sin Spring; lanzar excepciones de dominio tipadas; emitir domain events | importar Spring/Jackson/servlet; conocer DTOs; hacer I/O; leer configuración |
| **Application** | orquestar casos de uso; abrir la transacción; cargar agregados vía repos; validar autorización por recurso; publicar al outbox; ser la ÚNICA puerta de entrada de otros módulos | contener reglas de negocio (van al domain); construir responses HTTP; capturar excepciones para silenciarlas |
| **Infrastructure** | implementar puertos; traducir errores técnicos (constraint violations → excepciones de dominio del catálogo) | contener lógica de negocio; ser importada por otro módulo |
| **API** | mapear DTO↔dominio; declarar validación de forma (Bean Validation); documentar OpenAPI | tocar repositorios; abrir transacciones; contener condicionales de negocio |
| **Shared** | kernel técnico transversal (Money, DomainEvent, Problem mapping, idempotencia, outbox, JWT) | conocer ningún módulo de negocio |

## 2. Controllers

**Puede:** recibir request, delegar en UN caso de uso, mapear resultado a DTO envelope `{data}`, fijar status/`Location`.
**MUST NOT:** contener lógica condicional de negocio, acceder a repositorios, manejar transacciones, capturar excepciones de dominio (las traduce el `@ControllerAdvice` global a RFC 9457), loggear datos sensibles.
- Un controller **MUST** corresponder a un tag de OpenAPI y sus métodos al `operationId` exacto del contrato.

## 3. Casos de uso (application)

**Puede:** validar precondiciones consultando agregados, ejecutar transiciones del dominio, coordinar 2+ agregados en una TX, publicar eventos al outbox, retornar modelos de dominio o resultados tipados.
**MUST NOT:** decidir reglas que el agregado puede decidir (anemic domain prohibido), llamar a otros casos de uso del MISMO módulo en cadena (extraer servicio de dominio), invocar HTTP/proveedores dentro de un lock de fila (regla de la auditoría: el pago ocurre fuera del lock de inventario), retornar entidades JPA al controller sin pasar por dominio.
- Nombre: verbo + agregado + `UseCase` (`PublishTicketUseCase`, `ApproveRefundUseCase`), un caso de uso público por operación de negocio.

## 4. Convenciones de nombres (normativas)

| Concepto | Convención | Ejemplo |
|---|---|---|
| Use Case | `<Verbo><Agregado>UseCase` | `ReserveListingUseCase` |
| Servicio de dominio | `<Concepto>DomainService` o nombre del concepto | `ExchangePricing` |
| Controller | `<Recurso>Controller` | `ExchangeListingController` |
| DTO request/response | espejo EXACTO del schema OpenAPI | `CreateOrderRequest`, `OrderResponse` |
| Mapper | `<Agregado>ApiMapper` / `<Agregado>PersistenceMapper` | `TicketApiMapper` |
| Repository (puerto, en domain) | `<Agregado>Repository` | `TicketRepository` |
| Adapter JPA | `Jpa<Agregado>Repository` (implementa el puerto) | `JpaTicketRepository` |
| Puerto de salida | `<Capacidad>Port` | `PaymentProviderPort`, `NotificationPort` |
| Adapter | `<Tecnología><Capacidad>Adapter` | `FakePaymentAdapter` |
| Specification | `<Regla>Specification` | `TicketListableSpecification` |
| Policy | `<Concepto>Policy` (snapshot de EventPolicy en dominio) | `RefundPolicy` |
| Domain Event | pasado, sin sufijo Event | `TicketTransferred` |
| Integration Event (payload outbox) | = domain event + envelope versionado (api/08) | — |
| Command/Query (input de use case) | `<Verbo><Agregado>Command` / `<...>Query` (records inmutables) | `PayOrderCommand` |
| Factory | `<Agregado>Factory` solo si la creación tiene reglas | `TicketFactory` |
| Value Object | sustantivo sin sufijo | `Money`, `QueuePosition` |
| Excepción de dominio | `<Regla>Exception` mapeada 1:1 a un `ErrorCode` del contrato | `ListingNotAvailableException → listing_not_available` |

## 5. Dependencias

**Permitidas:** Spring Boot starters (web, validation, data-jpa, security), Flyway, PostgreSQL driver, jjwt/nimbus (JWS), springdoc-openapi, Testcontainers/JUnit5/Mockito/ArchUnit (test), Micrometer.
**Prohibidas sin ADR:** Lombok (records + código explícito), MapStruct (mappers a mano: explícitos y depurables), mensajería externa (Kafka/Rabbit — el outbox la reemplaza en esta fase), caches distribuidos (Redis), librerías de utilidades redundantes (commons-lang para lo que hace el JDK), cualquier cliente HTTP fuera del puerto de pagos/notificaciones.

## 6. Cuándo usar cada mecanismo

| Mecanismo | Regla normativa |
|---|---|
| `@Transactional` | **MUST** en cada método público de use case que escriba; **MUST NOT** en controllers, dominio ni consumidores de outbox por lote completo (TX por evento). `readOnly = true` en queries. La TX **MUST** ser corta: sin llamadas HTTP dentro (payment-intent A2). |
| Domain Events | **MUST** emitirse por toda transición de estado relevante (catálogo api/08 cerrado); se persisten al outbox EN LA MISMA TX. **MUST NOT** usarse `ApplicationEventPublisher` síncrono para efectos de negocio. |
| Outbox | ÚNICO canal para notificaciones, auditoría y analytics (ADR-09). Dispatcher **MUST** consumir con `FOR UPDATE SKIP LOCKED`, TX por evento, reintentos con backoff y dedupe por `eventId`. |
| Scheduler | solo para materializar expiraciones dirigidas por `expires_at` (ADR-10) y jobs de reconciliación/limpieza. Handler **MUST** ser idempotente y usar el orden canónico de locks. **MUST NOT** haber timers en memoria como única defensa. |
| Async (`@Async`) | **MUST NOT** usarse para lógica de negocio; solo el dispatcher de outbox y envío efectivo de notificaciones. |
| Cache | **MAY** en lecturas puras de catálogo y `global_config` (TTL corto); **MUST NOT** cachearse nada que participe en decisiones de compra/estado (inventario, listings, QRs). |
| Optimistic Lock (`@Version`) | **MUST** en Event, EventPolicy, TicketType, Parking, GlobalConfig, Order, Ticket, RefundRequest (tablas con `version`); conflicto → `409 version_conflict` con `conflictVersion`. |
| Pessimistic Lock (`FOR UPDATE`) | **MUST** en filas calientes: inventario al vender, listing al reservar, plaza al asignar, cabeza de waitlist, QR+ticket en check-in. **Orden canónico obligatorio** (design/db/07-bd-06 §10): `tickets` → hijos → vecinos alfabético. **MUST NOT** usarse advisory locks. |

## 7. Validación (defensa en profundidad)

1. **Borde (api):** Bean Validation en DTOs = validaciones del OpenAPI, ni más ni menos. Falla → `422 validation_error` con `errors[]`.
2. **Aplicación:** autorización por recurso (propietario/organizador/staff asignado) ANTES de tocar el agregado; recurso ajeno → `404`.
3. **Dominio:** invariantes en el agregado; transición inválida → excepción de dominio.
4. **BD:** constraints (ya migradas). Violación por carrera **MUST** traducirse en infrastructure al `ErrorCode` de conflicto correspondiente, nunca a `internal_error`.

## 8. Auditoría y logging

- Auditoría **MUST** ocurrir SOLO como consumidor del outbox (nunca escritura inline a `audit_log`). El contexto (actor, IP, device, correlationId) **MUST** viajar en el envelope del evento.
- Logging: SLF4J estructurado `accion_snake key=value`; nivel INFO para hechos de negocio, WARN para conflictos esperados, ERROR solo con stacktrace para fallos no esperados. **MUST** incluir `correlationId` (MDC). **MUST NOT** loggearse: contraseñas, tokens, `qrToken`, PII innecesaria, cuerpos completos de request.

## 9. Manejo de errores

- Un ÚNICO `@ControllerAdvice` mapea excepciones → RFC 9457 (`ProblemDetail`) según el catálogo api/02. **MUST NOT** existir try/catch de presentación en controllers.
- Toda excepción de dominio nueva **MUST** registrarse en el catálogo `ErrorCode` (cambio aditivo de contrato) antes de usarse.
- **MUST NOT** silenciarse excepciones (catch vacío o solo log) — o se traduce, o se propaga.

## 10. Crear un módulo nuevo

Prohibido sin gobernanza (doc 09). Aprobado el módulo: seguir la plantilla del doc 07 al pie de la letra + DoR (doc 01 §6) + matriz de dependencias actualizada (doc 10) + tests ArchUnit que la verifiquen.
