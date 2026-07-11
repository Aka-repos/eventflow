# EventFlow BD — 7. Normalización, Revisión Crítica y Mejoras

## 1. Análisis de normalización

**Cumplimiento general**: todas las tablas están en 1FN (atómicas, sin grupos repetidos — los `JSONB` son documentos opacos para el modelo relacional, no atributos multivaluados consultables), 2FN (ninguna PK compuesta con dependencias parciales: `user_roles`, `favorites`, `sponsor_events`, `idempotency_keys` solo tienen atributos que dependen de la clave completa) y 3FN/BCNF (sin dependencias transitivas: categoría, zona, tarifa y evento están separadas; no hay determinantes que no sean claves).

**Desnormalizaciones deliberadas** (cada una con su control de consistencia):

| Caso | Por qué se acepta | Control |
|---|---|---|
| `ticket_types.sold_quantity` (derivable contando tickets) | la venta decrementa un contador bajo lock en vez de `COUNT(*)` bajo lock — crítico en ventas masivas | `CHECK (0 ≤ sold ≤ total)` + se actualiza en la misma TX que crea los tickets |
| `tickets.event_id` (derivable vía ticket_type) | consultas dominantes son por evento; evita un join en cada lectura caliente | **FK compuesta** `(ticket_type_id, event_id) → ticket_types(id, event_id)`: la copia no puede divergir |
| `tickets.original_price`, `policy_snapshot`, `exchange_listings.original_price`, `ticket_transfers.*` | **no son redundancia**: son hechos históricos congelados por diseño (ADR-03/14); actualizarlos sería el bug | append-only / inmutables |
| `orders.total_amount` (derivable de ítems) | el total cobrado es parte del contrato con el proveedor de pago | validación app en la misma TX (test obligatorio) |
| `event_checkins.event_id` (derivable vía ticket) | métricas en vivo por evento sin doble join | escrito en la misma TX del check-in |

Ningún otro caso: la disponibilidad de parking se **deriva** (se rechazó el contador), y no hay columnas calculadas de reporting en tablas transaccionales (eso vive en el ledger/proyecciones).

## 2. Revisión crítica del modelo (honesta)

| Riesgo / limitación | Evaluación y mitigación |
|---|---|
| **Reglas cross-table fuera del alcance declarativo** (Σ ítems = total; moneda homogénea por orden; buyer≠seller; checkin.event = ticket.event) | Inevitable sin triggers. Se asume en capa de aplicación con tests dedicados; triggers quedan como endurecimiento opcional futuro. |
| **`TEXT + CHECK` para enums** vs `ENUM` nativo | Elegido por evolucionabilidad: agregar un estado = alterar un CHECK (barato, transaccional); los ENUM nativos complican renombres/quitas. Costo: bytes extra por fila — aceptable. |
| **UUID v7 expone timestamp de creación** en el id | Aceptado: no es dato sensible aquí y el beneficio de localidad B-tree domina. Si se considerara sensible en alguna entidad, esa entidad usaría v4. |
| **Multi-moneda a medias** | `currency` viaja con cada monto (VO completo) pero el MVP opera una sola moneda; la homogeneidad por orden es responsabilidad de la app. Riesgo bajo, documentado. |
| **JSONB en `policy_snapshot` y `extra_policies`** | Correctamente acotado: nunca se filtra/joinea por dentro (no requieren GIN); son documentos de lectura. Si una política extra empezara a consultarse, se promueve a columna (regla explícita). |
| **`ledger` con referencia débil** (`reference_type/reference_id` sin FK) | Deliberado: el libro mayor no debe acoplarse al ciclo de vida de tablas operativas. Costo: la integridad de la referencia es responsabilidad del emisor del asiento (siempre misma TX). |
| **Particionado no incluido en MVP** | audit/ledger/checkins crecerán; diseño ya compatible (BIGINT + índices por fecha). Activar partición mensual al superar ~10⁷ filas. |
| **Supabase**: PostgREST/RLS | La app nunca toca Supabase directo; los schemas de negocio **no** se exponen a PostgREST y RLS queda activada-denegando por defecto como cinturón extra. El backend usa credencial dedicada con privilegios mínimos (sin DDL, sin UPDATE/DELETE en append-only). |
| **Pooler en modo transacción** | Prohibido depender de estado de sesión (advisory locks, `SET LOCAL` fuera de TX). El diseño solo usa row locks y `version`. |

**Veredicto**: el modelo soporta el dominio completo, las 16 mejoras autorizadas y los 5 niveles de prioridad de reglas (seguridad e integridad quedan protegidas *por debajo* del código). Listo para derivar migraciones Flyway tras aprobación.

## 3. Mejoras propuestas en esta fase (respecto al modelo del doc 04)

1. **Schemas PostgreSQL por módulo** (`identity`, `catalog`, `ticketing`, `commerce`, `parking`, `ops`): espeja el monolito modular, hace visibles las dependencias entre módulos (FKs cross-schema) y delimita futuras extracciones. Costo casi nulo.
2. **UUID v7 generado en la aplicación** (vs v4): inserciones ordenadas en el tiempo → menos fragmentación de índices B-tree en tablas de alta escritura.
3. **BIGINT IDENTITY para tablas append-only internas** (`ticket_history`, `ledger_entries`, `event_checkins`, `parking_checkins`, `audit_log`, `outbox_events`): nunca se exponen por API (la regla anti-enumeración no aplica), son de altísimo volumen y el entero secuencial es más compacto y da orden natural.
4. **`CITEXT` para email**: unicidad case-insensitive sin `lower()` en cada query.
5. **FKs tipadas en `order_items`** + CHECK de polimorfismo cerrado (corrige I1).
6. **FK compuesta `tickets(ticket_type_id, event_id)`**: desnormalización con consistencia física.
7. **`queue_seq` BIGINT inmutable para FIFO** (corrige I3): elimina renumeraciones y carreras.
8. **`dynamic_qrs` generalizado a TICKET|PARKING** (corrige I2): una sola autoridad de QRs con las mismas garantías de unicidad.
9. **`UNIQUE (ticket_id) WHERE result='GRANTED'`** en check-ins: "un boleto entra una vez" pasa de regla de código a ley física.
10. **`CHECK (list_price <= original_price)`** y **`CHECK (fee + seller = exchange_price)`**: antifraude económico en la propia BD.
11. **`UNIQUE (order_id) WHERE status='APPROVED'`** en payments + `UNIQUE (provider, provider_ref)`: anti doble cobro y anti doble webhook.
12. **`search_vector` TSVECTOR generado + GIN**: búsqueda de eventos sin motor externo.
13. **`INET` para IPs** en auditoría/checkins: validación y análisis por rango nativos.
14. **`idempotency_keys` con PK (user_id, key) + TTL**: scope por usuario y limpieza barata.
15. **`payments` sin Soft Delete** (ajuste consciente sobre ADR-16): un pago es inmutable; su cancelación es estado. Requiere tu aprobación explícita al desviarse de la lista literal de la ADR.

## 4. Puntos que requieren tu aprobación antes de generar SQL

1. Schemas por módulo (vs schema único `public`).
2. UUID v7 app-side + BIGINT en append-only internas.
3. `payments` sin `deleted_at` (desviación justificada de ADR-16).
4. Enums como `TEXT + CHECK` (vs ENUM nativo de PostgreSQL).
5. Reglas cross-table asumidas en aplicación sin triggers (lista explícita en 07-bd-06 §9).
