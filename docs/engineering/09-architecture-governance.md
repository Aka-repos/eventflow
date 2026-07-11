# EventFlow Engineering — 09. Gobernanza de la Arquitectura

## 1. Clases de cambio

| Clase | Ejemplos | Requiere |
|---|---|---|
| **Libre** (dentro del Blueprint) | implementar módulos del roadmap; refactors internos de un módulo que no tocan contratos; tests; docs de módulo; bump de dependencias PATCH/MINOR compatibles | PR normal + checklist doc 11 |
| **Aditivo controlado** | endpoint/campo/enum nuevo en OpenAPI (api/06 §2); migración Flyway aditiva; evento de dominio nuevo; propiedad de config nueva | PR + actualización del contrato/catálogo PRIMERO + lint/diff en CI |
| **Requiere ADR + aprobación explícita** | tocar cualquier Frozen Decision (doc 01 §13); módulo nuevo o fusión/división de módulos; dependencia nueva de las prohibidas (Lombok, Redis, mensajería…); cambio de estrategia (locks, outbox, offline); breaking change de API (v2); cambio del modelo de dominio o de una máquina de estados; excepción de seguridad |
| **Prohibido** | modificar migraciones aplicadas; violar la matriz de dependencias; exponer entidades; saltarse el DoD | — (rechazo automático) |

Formato y ciclo de vida de las ADR: doc 01 §8. Regla de oro: **el documento cambia antes que el código**.

## 2. Criterios para introducir un módulo nuevo

Se aprueba solo si TODAS se cumplen: (1) responsabilidad de negocio nueva que no cabe semánticamente en un módulo existente; (2) tiene agregados propios (no solo "un par de endpoints"); (3) su acoplamiento estimado respeta la matriz (≤ 3 dependencias síncronas salientes); (4) existe caso de uso real ya solicitado (YAGNI). Si no cumple (2), es un paquete dentro de un módulo existente.

## 3. Criterios para dividir un módulo

Dividir cuando ≥ 2 señales persistan: crece > ~8k líneas o > ~10 use cases con cohesión visiblemente baja; dos sub-áreas evolucionan con ritmos/razones de cambio distintos; los tests de un área rompen constantemente por cambios de la otra; aparecen dependencias internas circulares entre sub-paquetes. La división **MUST** venir con ADR, plantilla doc 07 y actualización de la matriz doc 10.

## 4. Cuándo introducir microservicios (extraer un módulo)

**No antes de que ocurra algo de esto, medido, no intuido:** (a) un módulo necesita escalar/desplegarse con cadencia radicalmente distinta (p. ej. `checkin` en noches de evento); (b) equipos separados bloqueándose en el mismo deploy; (c) requisitos de aislamiento de fallo/compliance. Precondición técnica ya prevista: fronteras por fachada + eventos, schema propio en BD. La extracción **MUST** empezar por módulos hoja orientados a eventos (`notifications`, `analytics`) y **MUST NOT** partir agregados transaccionales (una transferencia de exchange jamás cruza red).

## 5. Cuándo introducir mensajería (broker)

Cuando el outbox+dispatcher alcance límites medidos: throughput sostenido que el polling no drena (lag > minutos en p95), o primer consumidor externo al monolito. Migración prevista por diseño: el dispatcher publica al broker en lugar de invocar consumidores; el envelope api/08 §1 ya es el mensaje. **MUST NOT** adoptarse broker "para estar listos".

## 6. Cuándo introducir CQRS

Ya existe CQRS ligero (dashboards leen ledger/proyecciones). CQRS con modelos de lectura dedicados solo cuando: una consulta de producto no pueda servirse < 500 ms p95 con índices/vistas materializadas, o el dashboard degrade el OLTP medido. Empezar por proyecciones alimentadas por outbox (infra ya existente). Separar bases de datos requiere ADR mayor.

## 7. Cuándo introducir Event Sourcing

**Criterio por defecto: no.** El dominio ya conserva historia donde importa (ticket_history, transfers, ledger append-only) sin el costo de ES. Solo se consideraría (ADR mayor) si un regulador/negocio exigiera reconstrucción de estado a un punto en el tiempo para TODO un agregado y la historia actual no bastara.

## 8. Criterios de refactorización

- Regla del boy-scout dentro del módulo tocado: **MUST** dejarse igual o mejor, sin mezclar refactor masivo con feature (PRs separadas, `refactor:`).
- Refactor cross-módulo o de shared kernel ⇒ clase "ADR + aprobación".
- Disparadores objetivos: violación de límites del doc 06 §2, duplicación de conocimiento detectada 3ª vez, test que exige mocks excesivos (señal de acoplamiento), hotspot de cambios (mismo archivo en >30% de PRs del módulo).
- Todo refactor **MUST** estar cubierto por tests ANTES de mover código (caracterización si faltan).

## 9. Revisión periódica

Al cerrar cada módulo del roadmap **SHOULD** revisarse: métricas de cobertura y deuda, cumplimiento de la matriz (ArchUnit), y si alguna decisión congelada acumuló evidencia de fricción — en cuyo caso se abre ADR de propuesta (nunca cambio silencioso).
