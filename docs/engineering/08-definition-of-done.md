# EventFlow Engineering — 08. Definition of Done (por módulo)

Un módulo del roadmap (design/06) se declara TERMINADO únicamente cuando **TODOS** los ítems están marcados. Sin excepciones ni "lo cierro después".

## 1. Funcional

- [ ] Todos los casos de uso del módulo implementados (= todas las operaciones de su tag en `docs/api/05-openapi.yaml`).
- [ ] Máquinas de estado espejo exacto de design/04 §3 (transiciones inválidas rechazadas con excepción tipada).
- [ ] Reglas de negocio del módulo demostradas con tests de dominio (incluidas las cross-table asignadas: doc 04 §4).
- [ ] Autorización por recurso verificada (propietario/organizador/staff; ajeno ⇒ 404).

## 2. Calidad y pruebas

- [ ] TDD aplicado; suite completa verde localmente y en CI.
- [ ] Cobertura ≥ 80% líneas y ramas en domain+application (JaCoCo/Kover adjunto en la PR).
- [ ] Tests de concurrencia del módulo (si tiene filas calientes) e idempotencia (si tiene POST ⚡).
- [ ] Tests de seguridad MockMvc (401/403/404 según matriz api/04).
- [ ] ArchUnit del módulo en verde (dependencias doc 10).
- [ ] Cero tests `@Disabled` sin issue.

## 3. Contrato y datos

- [ ] `/v3/api-docs` sin drift contra `docs/api/05-openapi.yaml` (check de CI en verde); cambios aditivos, si hubo, mergeados primero al YAML con lint Redocly.
- [ ] DTOs y mappers completos en ambos lados (backend api/, Android data/remote+mapper) espejo del contrato.
- [ ] Migraciones Flyway: sin cambios, o migración aditiva nueva aplicando en contenedor limpio V1→Vn (verificado en CI).
- [ ] Convenciones de BD respetadas (RLS+policy, RESTRICT, índices FK, prefijos de constraints).

## 4. Transversales

- [ ] Domain events del módulo emitidos al outbox en la misma TX y consumidos (auditoría registrada vía consumidor — nunca inline).
- [ ] Logging estructurado con correlationId; sin datos sensibles en logs.
- [ ] Manejo de errores completo: toda excepción de dominio mapeada a su `ErrorCode` del catálogo api/02; nada cae a `internal_error` por rutas conocidas.
- [ ] Scheduler del módulo (si aplica) idempotente y probado.
- [ ] Idempotency-Key operativa en todos los POST ⚡ del módulo.

## 5. Android integrado

- [ ] Feature espejo implementado (pantallas, ViewModels, repos, Room según matriz offline api/09 §3).
- [ ] Estados de UI completos: loading / content / error / empty / offline (componentes del design system).
- [ ] Errores mapeados por `code` a UX específica (doc 03 §7).
- [ ] Tests de ViewModel + repositorio + DAO del feature en verde.
- [ ] Flujo E2E del módulo demostrado contra backend local (evidencia en la PR).

## 6. Documentación y cierre

- [ ] README del módulo (doc 07 §2) creado/actualizado.
- [ ] Documentos de diseño actualizados SOLO si hubo ADR aprobada (frozen decisions intactas).
- [ ] Checklist de PR (doc 11) adjunto y completo; revisiones de agentes ejecutadas (code-reviewer; security-reviewer si el módulo es sensible).
- [ ] **Sin deuda técnica crítica**: cero TODOs sin issue, cero warnings nuevos de build, cero supresiones de linter sin justificación escrita.
- [ ] Squash-merge a `main` con Conventional Commit; tag MINOR si corresponde (doc 05 §4).
