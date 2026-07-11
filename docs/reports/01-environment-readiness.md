# Informe 01 — Environment Readiness (validación previa al Módulo 2)

- **Fecha:** 2026-07-10
- **Alcance:** auditoría de infraestructura Backend + Android + Supabase + Flyway + Docker + Testing + Seguridad. Sin cambios de negocio, dominio, arquitectura, ADRs, modelo de datos ni OpenAPI (todos congelados).
- **Método:** verificación en vivo — conexión real a Supabase, re-migración Flyway, backend levantado contra Supabase con batería E2E por HTTP, suites completas de tests re-ejecutadas.

---

## 1. Resumen ejecutivo

La plataforma **funciona de extremo a extremo como una sola pieza**: el backend arranca contra Supabase (PostgreSQL 17.6, TLS, UTC), Flyway gestiona el esquema con historial genuino V1–V9, y las 24 verificaciones E2E del contrato de autenticación (registro, login, rotación de refresh, detección de robo, logout idempotente, RFC 9457, Correlation ID) pasan contra la base real.

Se detectaron y **corrigieron 3 problemas de infraestructura** (1 crítico, 2 altos) que habrían bloqueado o degradado los módulos siguientes. Quedan **1 acción crítica manual** (rotar la contraseña de Supabase) y recomendaciones de severidad media/baja que no bloquean el Módulo 2.

| Suite | Resultado |
|---|---|
| Backend (unit + ArchUnit + IT Testcontainers) | **41/41 verdes**, cobertura domain+application **97.4% líneas / 87.5% ramas** |
| E2E HTTP contra Supabase real | **24/24 verificaciones** |
| Android (unit: fakes + Turbine + MockWebServer) | **17/17 verdes**, APK debug compila, 0 warnings |
| Drift de esquema Supabase vs migraciones | **0 diferencias** (938 líneas de DDL + 63 ACLs comparadas) |

---

## 2. Componentes verificados

### 2.1 Backend (Spring Boot 3.5.6, Java 17)

| Ítem | Estado | Evidencia |
|---|---|---|
| Estructura hexagonal (shared/config/modules) | ✅ | ArchUnit `DependencyRulesTest` (5 reglas) en verde |
| `application.yml` | ✅ | Variables por entorno con defaults solo-dev; sin secretos reales hardcodeados |
| Perfiles dev/test/prod | ⚠️ Medio | Un solo `application.yml`; tests inyectan datasource vía `@ServiceConnection`. Ver §6 |
| Variables de entorno | ✅ | `DB_URL`/`DB_USER`/`DB_PASSWORD`/`JWT_SECRET` externalizadas; ahora en `.env` (gitignoreado) |
| Flyway | ✅ | `create-schemas: false`, history en `public`; validate + migrate OK contra Supabase |
| JPA / Hibernate | ✅ | `ddl-auto: none` (Flyway único dueño), `open-in-view: false`, `hibernate.jdbc.time_zone: UTC` |
| DataSource / HikariCP | ✅ | Pool 10 conexiones contra pooler de sesión; conexión estable (arranque 38 s incluye migración inicial) |
| OpenAPI / Swagger | ✅ | `/v3/api-docs` 200, `/swagger-ui` 200 |
| Security / JWT | ✅ | Stateless, BCrypt, HS256 15 min + refresh opaco 14 d rotado; rutas públicas mínimas |
| Logging + Correlation ID + MDC | ✅ | Patrón `[cid=%X{correlationId}]`; header `X-Correlation-ID` se ecoa (verificado E2E) |
| Exception handler RFC 9457 | ✅ | `GlobalExceptionHandler` única autoridad (`spring.mvc.problemdetails` desactivado); `application/problem+json` con `code` verificado E2E |
| Bean Validation | ✅ | 422 con `errors[]` por campo (E2E y IT t04) |
| Jackson / UTF-8 / TimeZone | ✅ | Defaults de Boot 3 (UTF-8, ISO-8601); `Clock.systemUTC()` como bean; timestamps en BD `timestamptz` +00 |
| CORS | ⚠️ Bajo | Sin configuración — irrelevante para app nativa; necesario si se agrega cliente web. Ver §6 |
| Actuator | ✅ | Solo `/actuator/health` público (`{"status":"UP"}`); `/env`, `/beans`, raíz → 401 |

### 2.2 Base de datos (Supabase)

Verificado con `psql` real contra `aws-0-us-east-1.pooler.supabase.com:5432` (Shared Pooler **en modo sesión** — cumple auditoría A8: nunca el pooler transaccional 6543):

- **Conexión y SSL:** PostgreSQL 17.6, TLS negociado (`sslmode=require` en la URL JDBC), usuario y permisos correctos.
- **TimeZone:** UTC. **search_path:** `"$user", public, extensions`.
- **Esquema:** 37 tablas en 6 schemas (identity 6, catalog 6, ticketing 4, commerce 10, parking 3, ops 8), **37 policies RLS**, extensiones `citext`, `pgcrypto`, `uuid-ossp` presentes, rol `eventflow_app` (NOLOGIN) con los REVOKE de las 6 tablas append-only intactos.
- **Drift:** `pg_dump --schema-only` de Supabase vs. PostgreSQL 17 local con V1–V9 aplicadas → **diff vacío** (938 líneas DDL normalizadas). ACLs (GRANT/REVOKE): 63 vs 63, **diff vacío**.
- **Seed V9:** 4 roles + 6 filas de `ops.global_config` presentes.

### 2.3 Flyway

- **Hallazgo crítico corregido** (ver §3-C1 y §4): el esquema existía en Supabase pero **sin `flyway_schema_history`** (se aplicó manualmente en la fase de verificación del diseño). Tras la corrección: historial genuino con las 9 migraciones, checksums registrados, `success=t` en todas, `Successfully validated 9 migrations` en cada arranque.
- Orden y versionado correctos (V1→V9, sin pendientes, sin repetidas, sin fallidas). No se requiere `repair` ni `baseline`.

### 2.4 Android

- **Toolchain:** AGP 8.12.0, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Hilt 2.56.2, Room 2.7.1, Retrofit 2.11.0, OkHttp 4.12.0, compileSdk 36 / minSdk 26, Java 17, `kotlin.code.style=official`.
- **Red:** Retrofit + kotlinx-serialization (`ignoreUnknownKeys`, fallback `UNKNOWN` en enums), `ProblemConverter` RFC 9457, `AuthInterceptor` + `TokenAuthenticator` con cliente de refresh dedicado (sin recursión). **Timeouts explícitos añadidos en esta auditoría** (ver §4-F3).
- **Seguridad local:** tokens solo en `EncryptedSharedPreferences` (jamás Room); Room solo `session_user`; `network_security_config` bloquea cleartext salvo `10.0.2.2`; logging HTTP `BASIC` en debug / `NONE` en release (no vuelca tokens); `allowBackup=false`.
- **Build:** `testDebugUnitTest` 17/17 + `assembleDebug` OK (APK 12.2 MB), 0 warnings.

### 2.5 Comunicación Android → Backend

- **Nivel transporte/contrato (verificado E2E por HTTP contra Supabase):** login, registro, refresh con rotación, detección de reuso (revoca familia completa), logout idempotente, `Authorization: Bearer`, eco de `X-Correlation-ID`, header `Location` en 201, Problem Details con `code` en 400/401/409/422 — **24/24**.
- **Nivel cliente (verificado con MockWebServer):** parseo del envelope `{data}`, problems reales, timeouts y fallback de enums — 17/17.
- **Pendiente manual:** recorrido UI real en el emulador (Pixel 9 disponible) con el backend levantado — único paso que requiere interacción humana; no hay tests instrumentados de UI aún.

### 2.6 Docker / Testcontainers

- Docker Engine 29.4.0 + Compose v5.1.1 operativos. Workaround Docker 29 ↔ docker-java documentado y activo (`api.version=1.44` en la task `test`).
- Testcontainers levanta `postgres:17` real con migraciones Flyway reales (nunca H2) — IT re-ejecutada desde cero en esta auditoría.
- ⚠️ Bajo: no existe `docker-compose.yml` en el proyecto para el PostgreSQL local de desarrollo (ver §6).

### 2.7 Entorno local

| Herramienta | Versión | Estado |
|---|---|---|
| JDK | 17.0.19 (Temurin) para el proyecto; 23/25 también instaladas | ✅ (`gradle-daemon-jvm.properties` fija 17) |
| Gradle | wrapper 8.13 | ✅ |
| Android Studio | instalado | ✅ |
| SDK / adb | platform-tools 36.0.2 | ✅ |
| Emulador | AVD Pixel_9 | ✅ (apagado durante la auditoría) |
| Git | 2.50.1 | ✅ instalado — ⚠️ el repo **no está inicializado** |
| psql | no instalado | ℹ️ se usa vía `docker run postgres:17 psql` |

### 2.8 CI

No existe (consecuencia directa de no tener repo git). Ver §5/§6.

---

## 3. Problemas encontrados

| ID | Severidad | Problema | Estado |
|---|---|---|---|
| C1 | **Crítico** | Supabase tenía el esquema completo aplicado a mano **sin historial Flyway**: cualquier arranque del backend contra Supabase fallaba (`relation "users" already exists`). Además `baseline-on-migrate` no aplicaba porque Flyway evalúa el default-schema `public`, que estaba vacío. | ✅ Corregido |
| C2 | **Crítico** | La contraseña de Supabase quedó expuesta en texto plano (pegada en el chat de esta sesión y en un `.env` sin protección de gitignore). | ⚠️ Mitigado — **requiere rotación manual** |
| A1 | Alto | Clientes OkHttp sin timeouts explícitos: `callTimeout` es **ilimitado** por defecto; el cliente de refresh no tenía ninguno. Una petición colgada bloquearía el refresh de sesión indefinidamente. | ✅ Corregido |
| A2 | Alto | `API_BASE_URL` apunta a `http://10.0.2.2:8080` también en el buildType `release` (no hay override): un APK release apuntaría al emulador. Sin impacto hasta que exista un release. | Pendiente (bloquea release, no el Módulo 2) |
| A3 | Alto | `JWT_SECRET` tiene default embebido en `application.yml`: en producción arrancaría silenciosamente con el secreto de desarrollo en vez de fallar. | Pendiente (recomendación §6) |
| M1 | Medio | Sin perfiles Spring separados (`application-dev/prod.yml`); dev y prod comparten el mismo YAML con defaults. | Pendiente |
| M2 | Medio | Proyecto sin `git init`: sin historial, sin PRs, sin CI — incumple el flujo de docs/engineering/05. | Pendiente (decisión tuya) |
| M3 | Medio | Release build sin minify/R8 ni reglas ProGuard para los modelos serializados. | Pendiente |
| B1 | Bajo | Sin configuración CORS (solo relevante si aparece un cliente web). | Pendiente |
| B2 | Bajo | Sin `docker-compose.yml` para levantar el PostgreSQL local de desarrollo con un comando. | Pendiente |
| B3 | Bajo | La contraseña de Supabase es de baja entropía; al rotarla (C2) usar una generada aleatoriamente. | Pendiente |

---

## 4. Correcciones realizadas

**F1 — Reparación de Flyway en Supabase (C1).** Antes de tocar nada se verificó que la base solo contenía las 10 filas de seed (4 roles + 6 global_config, ambas recreadas por V9) y **cero datos de usuario**; y que el esquema no tenía drift (diff de `pg_dump` vacío, ACLs idénticas). Con eso confirmado, se optó por la reparación más limpia en lugar de un baseline artificial: `DROP SCHEMA ... CASCADE` de los 6 schemas + drop del historial parcial, y re-migración **real** de V1–V9 ejecutada por el propio Flyway del backend (`Successfully applied 9 migrations... now at version v9`). Resultado: historial genuino con checksums verificables — `flyway validate` pasa en cada arranque y las migraciones futuras (V10+) aplicarán con normalidad. V1 crea el rol `eventflow_app` con `IF NOT EXISTS`, por lo que el rol preexistente a nivel clúster no causó conflicto.

**F2 — Gestión de secretos (C2, parcial).** `.env` en la raíz normalizado (URL JDBC con `sslmode=require`, usuario, contraseña, `JWT_SECRET`) y añadido `.env` / `.env.*` al `.gitignore` (con excepción `!.env.example`). `application.yml` no contiene ningún secreto real. Uso: `set -a; source .env; set +a; ./gradlew -p backend bootRun`. **La rotación de la contraseña solo puedes hacerla tú** desde el dashboard de Supabase (Settings → Database → Reset password); tras rotarla, actualiza únicamente `.env`.

**F3 — Timeouts OkHttp (A1).** En `NetworkModule.kt` ambos clientes (principal y de refresh) ahora fijan `connect 10 s / read 30 s / write 30 s / call 45 s` como constantes nombradas. Compilado y con los 17 tests en verde.

Ninguna corrección tocó dominio, arquitectura, ADRs, modelo de datos ni OpenAPI.

---

## 5. Riesgos detectados (pendientes)

1. **Contraseña de Supabase comprometida hasta que se rote** (C2/B3) — es la acción manual más urgente.
2. **Ausencia de git/CI** (M2): sin trazabilidad de cambios ni gates automáticos; cuanto más crezca el código, más costoso el primer commit y mayor el riesgo de pérdida de trabajo (hoy no hay ninguna copia del código fuera de esta máquina).
3. **Arranque en prod con configuración de dev** (A3/M1): los defaults del YAML hacen que un despliegue mal configurado arranque "verde" con secreto y URL de desarrollo en lugar de fallar rápido.
4. **Release de Android no preparado** (A2/M3): URL base del emulador y sin minify. Irrelevante ahora, bloqueante para publicar.
5. **Pooler de sesión de Supabase**: límite de conexiones concurrentes del plan; con Hikari max 10 hay margen, pero si se añaden instancias del backend habrá que revisar el presupuesto de conexiones.

---

## 6. Recomendaciones antes del Módulo 2

1. **Rotar la contraseña de Supabase ya** y regenerar `.env` (5 minutos, elimina el riesgo #1).
2. **`git init` + primer commit + rama `main` protegida** siguiendo docs/engineering/05, y un workflow mínimo de CI (build + tests backend y Android). Es el pendiente del DoD del Módulo 0+1.
3. **Perfiles Spring**: `application-prod.yml` sin defaults en `DB_*`/`JWT_SECRET` (fail-fast si faltan) y `application-dev.yml` con los defaults actuales. Cambio de configuración puro, sin ADR.
4. **Android por buildType**: `buildConfigField` de `API_BASE_URL` diferenciado debug/release y activar R8 con reglas keep para los DTOs `@Serializable` cuando se prepare el primer release.
5. **`docker-compose.yml`** con `postgres:17` para desarrollo local sin depender de Supabase (y para trabajar offline).
6. **`.env.example`** commiteado con las claves sin valores, como documentación del contrato de entorno.
7. Al cerrar el primer flujo UI del Módulo 2, hacer el **recorrido manual app↔backend en el emulador** (login/registro reales) — único hueco de validación que quedó abierto.

---

## 7. Checklist final

| Componente | Estado |
|---|---|
| ✅ Backend listo | Arranca contra Supabase, config auditada, 41/41 tests |
| ✅ Android listo | 17/17 tests, APK debug OK, timeouts corregidos, 0 warnings |
| ✅ Supabase listo | Conexión TLS, UTC, esquema sin drift, RLS y privilegios intactos |
| ✅ Flyway listo | Historial genuino V1–V9, validate limpio, sin pendientes/fallidas |
| ✅ API lista | Contrato auth verificado E2E (24/24) + OpenAPI/Swagger servidos |
| ✅ Seguridad lista* | JWT+rotación+reuso OK, BCrypt, tokens cifrados, Actuator restringido — *pendiente rotar password de Supabase (acción manual)* |
| ✅ Testing listo | Unit + IT Testcontainers + ArchUnit + JaCoCo re-ejecutados en verde |
| ✅ **Proyecto listo para continuar con el Módulo 2** | Con las recomendaciones §6.1–6.2 como primeras tareas |
