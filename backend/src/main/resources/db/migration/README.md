# Migraciones Flyway — EventFlow

Esquema para **PostgreSQL 17 / Supabase**, derivado de la serie de diseño `docs/design/db/` (07-bd-01…07) y la auditoría técnica (08). No editar migraciones aplicadas: todo cambio posterior es una nueva `V<n>__`.

## Orden y contenido

| Versión | Contenido |
|---|---|
| V1 | Schemas por módulo, extensión `citext`, rol `eventflow_app` (NOLOGIN) |
| V2 | `identity` (users, roles, user_roles, refresh_tokens, user_devices) |
| V3 | `catalog` (categories, events, event_policies, event_zones, sponsors) + `identity.staff_assignments` |
| V4 | `ticketing` (ticket_types, tickets, ticket_history, dynamic_qrs) — FKs a commerce/parking diferidas |
| V5 | `commerce` (orders, order_items, payments, listings, reservations, transfers, waitlist, refunds, ledger) + FKs diferidas de tickets |
| V6 | `parking` (parkings, slots, reservations) + FKs diferidas de order_items y dynamic_qrs |
| V7 | `ops` (checkins, favorites, notifications, global_config, idempotency, outbox, audit) |
| V8 | Privilegios mínimos, `REVOKE UPDATE/DELETE` en append-only, bloqueo de `anon`/`authenticated`, RLS |
| V9 | Semillas: roles y configuración global |

Las FKs circulares (`ticketing ↔ commerce ↔ parking`) se resuelven con `ALTER TABLE … ADD CONSTRAINT` en la migración del último participante; el ciclo es inherente al dominio (el comercio emite boletos y los boletos se recomercializan).

## Configuración requerida (Spring Boot)

```yaml
spring:
  flyway:
    default-schema: public   # history table en public; V1 es dueño de los schemas
    create-schemas: false    # la conexión de gestión de Flyway bloquearía los ALTER de V8 (RLS)
  datasource:
    # Supabase: SIEMPRE la conexión directa (puerto 5432) o pooler en modo sesión.
    # El pooler en modo transacción (6543) degrada prepared statements de Hibernate
    # y prohíbe estado de sesión (auditoría A8).
    url: jdbc:postgresql://<host>:5432/postgres
  jpa:
    properties:
      hibernate.default_schema: ""   # cada @Table declara su schema
```

## Convenciones vigentes

- **PKs**: UUID **v7 generado por la aplicación** (`gen_random_uuid()` v4 es solo fallback operativo). Append-only internas usan `BIGINT IDENTITY`.
- **`updated_at` la mantiene la aplicación** (`@UpdateTimestamp`); no hay triggers. Si se opera por SQL manual, actualizarla explícitamente.
- **Soft Delete**: `deleted_at` en users/events/tickets/orders/refund_requests. En JPA usar `@SQLRestriction("deleted_at IS NULL")` (no `@Where`, deprecado).
- **Enums**: `TEXT + CHECK`. Agregar un valor = `ALTER TABLE … DROP CONSTRAINT` + `ADD CONSTRAINT` en una migración nueva.
- **Migraciones futuras**: toda tabla nueva debe (1) habilitar RLS + policy `app_full_access` (V8 solo cubre las existentes), (2) respetar la política ON DELETE RESTRICT, (3) llevar índices en columnas FK consultadas.
- **Reglas cross-table en capa de aplicación** (test obligatorio, ver 07-bd-06 §9): `orders.total_amount = Σ ítems`; moneda homogénea por orden; buyer ≠ seller en reservas del Exchange; ADR-19 (`acquired_via='EXCHANGE'` no puede solicitar reembolso).
- **Usuario LOGIN del backend**: se crea fuera de las migraciones y se le asigna el rol: `CREATE ROLE eventflow_backend LOGIN PASSWORD '…'; GRANT eventflow_app TO eventflow_backend; ALTER ROLE eventflow_backend SET ROLE eventflow_app;`
