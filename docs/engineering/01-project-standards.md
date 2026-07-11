# EventFlow Engineering — 01. Estándares Generales del Proyecto

> Palabras clave **MUST / MUST NOT / SHOULD / SHOULD NOT / MAY** según RFC 2119. Este Blueprint tiene prioridad sobre cualquier decisión tomada durante la implementación; modificarlo requiere una ADR nueva con aprobación explícita.

## 1. Estructura del repositorio (monorepo)

```text
EventFlow/
├── app/                          # Aplicación Android (Kotlin + Compose)
├── backend/                      # API Spring Boot
│   └── src/main/resources/db/migration/   # Flyway (V1–V9 ya generadas)
├── docs/
│   ├── design/                   # Diseño de dominio y arquitectura (00–06) + db/ (07–08)
│   ├── api/                      # Contrato API First (01–09, openapi.yaml = fuente de verdad)
│   ├── engineering/              # Este Blueprint (01–11)
│   └── adr/                      # ADRs nuevas post-congelamiento (ADR-020+)
└── README.md
```

- Los módulos backend **MUST** vivir bajo `backend/src/main/java/com/eventflow/` con la estructura del doc 07.
- **MUST NOT** crearse carpetas de código fuera de `app/` y `backend/`.

## 2. Organización por módulos

Los módulos backend permitidos son exactamente los del diseño (doc design/03 §2.1): **15 módulos de negocio** — `modules/{identity, catalog, ticketing, ordering, payments, exchange, waitlist, refunds, parking, checkin, notifications, analytics, ledger, platformconfig, audit}` — más los paquetes técnicos `shared` y `config`.

- **MUST NOT** crearse módulos nuevos sin el proceso de gobernanza (doc 09 §3).
- **MUST NOT** existir código de negocio en `shared` (solo kernel técnico: Money, DomainEvent, errores, idempotencia, outbox, seguridad).
- Cada módulo Android de feature **MUST** vivir en `ui/feature/<nombre>` según design/03 §3.1.

## 3. Convenciones de nombres

| Ámbito | Regla |
|---|---|
| Paquetes backend | `com.eventflow.modules.<modulo>.<capa>` — minúsculas, sin guiones |
| Paquetes Android | `com.app.eventflow.<capa>.<detalle>` (estructura design/03 §3.1) |
| Clases | PascalCase; sufijos normativos del doc 02 §4 |
| BD | snake_case; convenciones ya congeladas en `backend/.../db/migration/README.md` |
| JSON | camelCase; enums UPPER_SNAKE (contrato api/01 §5) |
| Ramas/commits | doc 05 |
| Archivos de docs | `NN-nombre-kebab.md` numerados por serie |

## 4. Reglas de dependencias (resumen; matriz completa en doc 10)

- `domain` **MUST NOT** depender de Spring, JPA-específicos de otro módulo, ni de otras capas.
- Un módulo **MUST NOT** importar `infrastructure` ni `api` de otro módulo; la comunicación entre módulos **MUST** ser vía interfaz pública de `application` o eventos de dominio (outbox).
- Android: `domain` puro Kotlin; `ui` **MUST NOT** importar `data.remote`/`data.local` directamente.

## 5. Reglas de visibilidad

- Backend: clases de `infrastructure` y `application` internas al módulo **MUST** ser package-private cuando el lenguaje lo permita; solo la fachada de `application` (puertos públicos) y los eventos son públicos.
- Android: composables de feature **MUST** ser `internal` al feature salvo los de `ui/components` (design system).
- **MUST NOT** exponerse entidades JPA/Room fuera de su capa (mappers obligatorios).

## 6. Definition of Ready (para iniciar un módulo)

Un módulo **MUST NOT** iniciarse sin: (1) sus endpoints existen en `docs/api/05-openapi.yaml`; (2) sus tablas existen en migraciones Flyway aplicadas; (3) sus reglas de dominio están en docs/design; (4) sus dependencias (doc 10) están implementadas o simuladas tras puerto; (5) el orden del roadmap (design/06) se respeta o se justifica la desviación.

## 7. Definition of Done

Checklist obligatorio en doc 08. Sin excepciones.

## 8. Convenciones ADR

- Las ADR 01–19 viven en `docs/design/00-vision-y-decisiones.md` y están **congeladas**.
- ADRs nuevas **MUST** crearse como `docs/adr/ADR-0NN-titulo-kebab.md` con secciones: Contexto · Decisión · Alternativas descartadas · Consecuencias · Estado (Propuesta/Aprobada/Superseded).
- Una ADR **MUST** ser aprobada explícitamente por el propietario del proyecto antes de implementarse.

## 9. Convenciones de documentación

- Idioma: español; términos técnicos en inglés cuando son estándar (commit, endpoint, listing).
- Documento reemplazado **MUST** marcarse con banner `⚠️ SUPERSEDED` apuntando al sucesor (patrón ya aplicado en design/05).
- Cada serie mantiene numeración estable; **MUST NOT** renumerarse documentos publicados.
- Archivos **SHOULD** mantenerse < 800 líneas (partir por tema si crecen).

## 10. Convenciones UML y Mermaid

- Diagramas de secuencia, estados y ER: **MUST** usar Mermaid (renderiza en IDE/GitHub).
- Diagramas de casos de uso: **MUST** usar PlantUML (Mermaid no los soporta).
- Todo diagrama **MUST** ser texto versionado en el repo (nunca imágenes binarias como fuente).
- Las máquinas de estado en código **MUST** reflejar exactamente los `stateDiagram` de design/04; ante duda, el diagrama manda.

## 11. Convenciones OpenAPI

- `docs/api/05-openapi.yaml` es la fuente de verdad congelada. El backend **MUST** implementar exactamente ese contrato; el `/v3/api-docs` generado **MUST** compararse contra el YAML en CI (drift = build roto).
- Cambios: solo aditivos según api/06; **MUST** validar con Redocly antes de mergear.
- `operationId` **MUST** ser único y estable (es el nombre del método Retrofit en Android).

## 12. Convenciones Flyway

Las del `backend/.../db/migration/README.md` son normativas. Además: una migración **MUST** pertenecer a un solo módulo funcional; **MUST NOT** editarse migraciones aplicadas; toda tabla nueva **MUST** habilitar RLS + policy, respetar ON DELETE RESTRICT por defecto e indexar FKs consultadas; nombres de constraints **MUST** seguir los prefijos `pk_ fk_ uq_ ck_ ix_`.

## 13. Frozen Decisions

Quedan **CONGELADAS** y **MUST NOT** modificarse durante esta fase sin ADR aprobada:

1. Arquitectura general (monolito modular hexagonal; Android Clean Architecture offline-first).
2. ADR-01…ADR-19 (design/00).
3. Modelo de dominio, agregados y bounded contexts (design/db/07-bd-01).
4. Modelo de base de datos y migraciones V1–V9 verificadas (design/db + backend/db/migration).
5. Contrato OpenAPI 3.1 y DTOs (docs/api, 79 operaciones) — evolución solo aditiva (api/06).
6. Contrato Android y matriz offline (api/09): compras/pagos/check-in **jamás** offline; solo favoritos se encolan.
7. Modelo de eventos de dominio + outbox (api/08, design/03 §2.4).
8. Estrategias de persistencia, seguridad, auditoría, concurrencia (orden canónico de locks) e integridad (design/db/07-bd-06, auditoría 08).
9. Modelo financiero: ledger de partida doble, comisión solo en exchange exitoso, reembolso 100% de `acquisitionPrice`, regla de redondeo M1.
10. Official Ticket Exchange, Waitlist FIFO polimórfica, QR dinámicos firmados y gestión del derecho de asistencia (incl. ADR-19: reembolso exclusivo de compra primaria).

**Protocolo ante mejora detectada:** describir problema → justificar técnicamente → ventajas/desventajas → impacto estimado → solicitar aprobación. **MUST NOT** implementarse sin aprobación explícita.
