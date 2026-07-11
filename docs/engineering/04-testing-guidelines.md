# EventFlow Engineering — 04. Estrategia de Testing

## 1. Pirámide y cobertura

| Nivel | Alcance | Herramientas | Proporción esperada |
|---|---|---|---|
| Unit | dominio (agregados, máquinas de estado, pricing, policies), use cases con puertos mockeados, ViewModels, mappers | JUnit 5 + Mockito (Java) · JUnit + MockK + Turbine (Kotlin) | ~70% |
| Integration | repositorios contra PostgreSQL real, controllers (MockMvc), seguridad, outbox, scheduler, concurrencia | Testcontainers (postgres:17) + Flyway real + MockMvc + Spring Security Test | ~25% |
| E2E | flujos críticos: compra→pago→QR→check-in; publicar→reservar→transferir; reembolso→waitlist | SpringBootTest full context + app instrumentada (Compose UI tests) | ~5% |

- Cobertura **MUST** ≥ 80% líneas y ramas en `domain` + `application` (JaCoCo backend; Kover en `domain`/`data` Android). `api`/`infrastructure` **SHOULD** ≥ 70%. Config/DTOs generables quedan excluidos del cómputo.
- TDD **MUST** aplicarse: test primero (RED) → implementación mínima (GREEN) → refactor. Un bug corregido **MUST** llegar con el test que lo reproduce.
- CI **MUST** fallar si la cobertura baja del umbral o si algún test es `@Disabled` sin issue vinculado.

## 2. Naming y estructura

- Backend: `<Clase>Test` (unit) / `<Clase>IT` (integration); métodos `should_<resultado>_when_<condicion>`. Given/When/Then como bloques comentados **MUST** estructurar cada test.
- Kotlin: nombres con backticks `` `emite Error cuando el pago falla` ``.
- Un test **MUST** verificar UN comportamiento; **MUST NOT** haber asserts de conveniencia sobre efectos no relacionados.
- Espejo de paquetes: el test vive en el mismo paquete que la clase probada.

## 3. Datos de prueba

- **Builders/Object Mothers** obligatorios por agregado en `testFixtures` (backend) / `testShared` (Android): `aTicket()`, `aPaidOrder()`, `anEventWithPolicy()` — defaults válidos, personalización por parámetros nombrados.
- **MUST NOT** duplicarse literales de setup en tests (usar builders); **MUST NOT** depender de datos de seed salvo roles/config (V9).
- Testcontainers **MUST** ejecutar las migraciones Flyway reales (V1–V9) — el esquema de test ES el de producción; **MUST NOT** usarse H2 ni `ddl-auto`.
- Cada IT **MUST** aislar sus datos (truncate por módulo o `@Transactional` rollback donde no interfiera con locks probados).

## 4. Cómo probar cada mecanismo (normativo)

| Mecanismo | Estrategia obligatoria |
|---|---|
| **Máquinas de estado** | test unitario por transición válida + test por CADA transición inválida (debe lanzar la excepción de dominio tipada). La matriz completa de design/04 §3 **MUST** quedar cubierta. |
| **Domain Events** | unit: el use case deja los eventos esperados en el agregado/colector. Integration: tras commit, la fila existe en `ops.outbox_events` con `eventType`, `payload.eventVersion` y envelope correcto — en la MISMA TX (verificar que un rollback no deja evento). |
| **Outbox dispatcher** | IT con 2 consumidores concurrentes sobre el mismo lote → `SKIP LOCKED` garantiza sin doble proceso; reintento tras fallo incrementa `attempts` y conserva `PENDING/FAILED`; dedupe por `eventId` en consumidor. |
| **Concurrencia** | IT con `ExecutorService` + `CountDownLatch` (o `CompletableFuture` en barrera): 2 hilos compran el último boleto / reservan el mismo listing / check-in del mismo QR ⇒ exactamente uno gana y el otro recibe la excepción de conflicto; el estado final de BD es consistente (constraint no violada). **MUST** existir para: inventario, listing, plaza, cabeza de waitlist, QR. |
| **Transacciones** | IT que fuerza excepción a mitad del use case y verifica rollback completo (sin ticket huérfano, sin outbox, contador intacto). |
| **Idempotencia** | IT: misma `Idempotency-Key` dos veces ⇒ misma respuesta persistida, efectos UNA vez (un solo pago/orden en BD); misma key con body distinto ⇒ `422 idempotency_key_reuse`. |
| **Scheduler** | el handler se prueba invocándolo directamente (IT) con filas `expires_at` vencidas y no vencidas; **MUST** verificarse idempotencia (segunda ejecución no cambia nada). El trigger `@Scheduled` NO se espera en tests (sin sleeps). |
| **Optimistic lock** | IT: cargar versión N, actualizar concurrentemente, segunda escritura ⇒ excepción → mapeo a `409 version_conflict` con `conflictVersion` (MockMvc). |
| **Cache** | unit del TTL/invalidación del adapter; IT verifica que cambiar `global_config` invalida la entrada. |
| **Seguridad** | MockMvc por endpoint: sin token → 401; rol incorrecto → 403; recurso ajeno → **404**; STAFF solo su evento asignado. Matriz de autorización de api/04 **MUST** quedar cubierta por tests parametrizados. |
| **Contrato API** | IT de controller valida status, envelope `{data}`, y Problems RFC 9457 (`code`, `traceId`); CI compara `/v3/api-docs` contra `docs/api/05-openapi.yaml` (drift = fallo). |
| **Reglas cross-table** | los 4 invariantes delegados a aplicación (README migraciones) **MUST** tener test dedicado: Σ ítems = total; moneda homogénea; buyer ≠ seller; ADR-19 (EXCHANGE no reembolsa). |

## 5. Android

- ViewModels: MockK + Turbine sobre `StateFlow`/`UiEffect`; dispatcher de test inyectado (`StandardTestDispatcher`), sin `Thread.sleep`.
- Repositorios: fake DAOs en memoria + MockWebServer para Retrofit (incluye parseo de Problems y rotación de token del Authenticator).
- Room DAOs: Robolectric o instrumentados con BD en memoria.
- Compose: UI tests de los componentes del design system y de los 5 estados de pantalla (loading/content/error/empty/offline).
- Flujos E2E instrumentados mínimos: login, compra con FAKE provider, ver QR con countdown, publicar en exchange.

## 6. Reglas de calidad de tests

- **MUST NOT**: sleeps arbitrarios (usar awaitility/latches/test dispatchers), asserts sobre mensajes de texto de UI del servidor (`detail`), mocks de tipos que se poseen sin necesidad (preferir fakes del puerto), tests que dependan del orden de ejecución.
- Flaky test = bug: se arregla o se elimina con issue; **MUST NOT** ignorarse con reintentos de CI.
- ArchUnit **MUST** verificar en CI las reglas de dependencia (doc 10) — los tests de arquitectura son parte de la suite.
