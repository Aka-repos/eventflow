# EventFlow Engineering — 11. Checklist de Revisión Arquitectónica (por PR)

> **MUST** adjuntarse completo en la descripción de toda PR (doc 05 §6). Un ítem no aplicable se marca `N/A` con justificación de una línea. Cualquier ítem en rojo bloquea el merge.

## 1. Arquitectura y DDD

- [ ] Capas respetadas: sin lógica en controllers/ViewModels; reglas en agregados; use cases solo orquestan.
- [ ] Sin fugas: ninguna entidad JPA/Room ni DTO cruza capas indebidas; mappers presentes.
- [ ] Matriz de dependencias (doc 10) intacta; ArchUnit en verde; sin imports a domain/infrastructure/api de otro módulo.
- [ ] Máquinas de estado idénticas a design/04 §3; ninguna transición nueva sin ADR.
- [ ] Lenguaje ubicuo: nombres del código = nombres del diseño (Listing, WaitlistOffer, no sinónimos inventados).
- [ ] Ninguna Frozen Decision (doc 01 §13) tocada sin ADR aprobada y referenciada.

## 2. SOLID y código

- [ ] Límites del doc 06 §2 (métodos ≤50, params ≤4, complejidad ≤10, anidamiento ≤4) o justificación explícita.
- [ ] Cero antipatrones de la lista cerrada (doc 06 §6).
- [ ] Inmutabilidad: records/data class/val; sin setters de negocio; dinero solo vía `Money`.
- [ ] Dependencias nuevas: ninguna, o permitidas (docs 02 §5 / 03 §8), o ADR.

## 3. Seguridad

- [ ] Autorización por recurso en cada use case nuevo (propietario/organizador/staff); ajeno ⇒ 404.
- [ ] Ningún precio/monto/elegibilidad aceptado del cliente; el servidor calcula.
- [ ] Sin secretos, tokens ni PII en código, logs, eventos o mensajes de error.
- [ ] Endpoints nuevos con roles correctos según matriz api/04; tests 401/403/404 presentes.
- [ ] Si toca auth/pagos/QR/exchange/reembolsos: revisión security-reviewer ejecutada y adjunta.

## 4. Concurrencia y transacciones

- [ ] `@Transactional` en el lugar correcto (use case), TX corta, sin HTTP dentro (patrón payment-intent).
- [ ] Locks pesimistas solo en filas calientes previstas y respetando el **orden canónico** (design/db/07-bd-06 §10).
- [ ] Optimistic lock manejado: `version_conflict` con `conflictVersion`; UI recarga.
- [ ] Operación crítica nueva ⇒ test de carrera (2 hilos) presente.
- [ ] POST ⚡ nuevo ⇒ Idempotency-Key exigida + test de repetición.

## 5. Eventos, auditoría y outbox

- [ ] Toda transición relevante emite su evento del catálogo api/08 EN LA MISMA TX (verificado por test).
- [ ] Ningún efecto secundario inline (notificación/auditoría directa) — solo consumidores.
- [ ] Evento nuevo: envelope versionado, agregado al catálogo api/08, consumidores idempotentes.

## 6. Contrato y datos

- [ ] OpenAPI: sin drift (check CI); cambios solo aditivos con lint Redocly verde; operationIds estables.
- [ ] Errores nuevos registrados en el catálogo api/02 y mapeados en el ControllerAdvice.
- [ ] Flyway: solo migraciones nuevas aditivas; convenciones del README de migraciones; aplican V1→Vn en limpio.
- [ ] DTOs congelados intactos; espejo Android actualizado si hubo adición.

## 7. Testing

- [ ] TDD evidente (tests del comportamiento nuevo, no tests-después decorativos).
- [ ] Cobertura ≥ 80% domain+application del módulo tocado (reporte adjunto).
- [ ] Sin sleeps/flakiness/orden implícito; fixtures vía builders.
- [ ] Integración con Testcontainers para todo acceso a datos nuevo.

## 8. Logging y observabilidad

- [ ] Hechos de negocio nuevos logueados estructurado con correlationId (MDC); niveles correctos.
- [ ] Errores esperados en WARN sin stacktrace; inesperados en ERROR con stacktrace y traceId.
- [ ] Métricas Micrometer para operaciones críticas nuevas (contador de conflictos, latencia de pago) **SHOULD**.

## 9. Deuda técnica y cierre

- [ ] Cero TODOs sin issue; cero warnings nuevos; cero supresiones sin justificación.
- [ ] README del módulo actualizado (eventos, excepciones↔ErrorCode, dependencias).
- [ ] DoD (doc 08) actualizado si la PR cierra el módulo.
- [ ] Commit/PR según doc 05 (Conventional Commits, squash, un módulo por PR).
