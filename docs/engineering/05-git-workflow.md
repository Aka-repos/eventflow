# EventFlow Engineering — 05. Flujo Oficial de Git

## 1. Estrategia: Trunk-Based con ramas cortas (GitHub Flow)

- `main` es la única rama permanente y **MUST** estar siempre verde y desplegable.
- Todo cambio **MUST** entrar por Pull Request desde una rama corta (< 3 días de vida, **SHOULD** < 400 líneas de diff). Trabajo grande se parte en PRs incrementales detrás del roadmap.
- **MUST NOT** haber commits directos a `main` ni ramas `develop`/release permanentes (no aportan con un solo entorno; se reevalúa en gobernanza si aparecen releases paralelos).

## 2. Ramas — naming

`<tipo>/<modulo>-<descripcion-kebab>` · tipos: `feat | fix | refactor | docs | test | chore | perf | ci | hotfix`
Ejemplos: `feat/identity-refresh-rotation`, `fix/exchange-reservation-expiry`, `docs/engineering-blueprint`.

## 3. Conventional Commits (obligatorio)

```
<tipo>(<módulo>): <descripción en minúscula, imperativo, ≤ 72 chars>

<cuerpo opcional: qué y por qué, no cómo>
```
- Tipos = los de §2. Scope **MUST** ser el módulo (`identity`, `exchange`, `app-tickets`, `db`, `api`, `engineering`).
- Breaking change (solo posible vía ADR aprobada): `!` tras el scope + sección `BREAKING CHANGE:` en el cuerpo.
- **MUST NOT**: commits `wip`, `fix2`, mezclas de refactor+feature en un commit, commits que rompen la build (bisectabilidad).

## 4. Versionado (SemVer)

- Versión del producto en tags `vMAJOR.MINOR.PATCH` sobre `main`.
- MINOR = módulo/funcionalidad completada (DoD, doc 08); PATCH = fixes; MAJOR = breaking del contrato API (requiere ADR + `/api/v2`).
- La versión del contrato OpenAPI evoluciona según api/06 §6, independiente de la del producto.

## 5. Releases y hotfix

- Release = tag anotado en `main` + notas generadas desde Conventional Commits.
- Hotfix: rama `hotfix/<descripcion>` desde el tag afectado → PR acelerada (1 aprobación + CI verde, sin excepción de tests) → merge a `main` + tag PATCH.

## 6. Pull Requests

- Título **MUST** seguir Conventional Commits (squash merge lo convierte en el commit).
- Descripción **MUST** incluir: qué/por qué, módulos tocados, checklist del doc 11 marcado, plan de prueba, y referencia a la ADR si hubo decisión arquitectónica.
- Una PR **MUST** pertenecer a UN módulo del roadmap (cross-módulo solo con justificación).
- **Merge rule: squash merge** únicamente (historia lineal 1 PR = 1 commit); **MUST NOT** usarse merge commits ni rebase-merge.

## 7. Code Review

- Toda PR **MUST** pasar: (1) CI completa (build, tests, cobertura, ArchUnit, lint OpenAPI + diff de compatibilidad, migraciones Flyway aplicadas en contenedor limpio); (2) revisión con el checklist del doc 11; (3) revisión de seguridad adicional si toca auth, pagos, QR, exchange o reembolsos (agentes code-reviewer/security-reviewer).
- El autor **MUST NOT** aprobar su propia PR cuando exista otro revisor disponible; en trabajo en solitario, la revisión con checklist + agentes es obligatoria y se adjunta como comentario.

## 8. Protección de `main` (política obligatoria)

- Require PR + status checks verdes (build/tests/cobertura/ArchUnit/OpenAPI-diff) antes de merge.
- Prohibido force-push y borrado de `main`.
- Historia lineal exigida (consecuencia del squash-only).
- Ramas mergeadas **MUST** borrarse automáticamente.
- Tags `v*` protegidos (solo mantenedor).
