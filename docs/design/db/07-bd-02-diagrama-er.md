# EventFlow BD — 2. Diagrama Entidad-Relación (físico)

> Organización física en **schemas PostgreSQL por módulo** (mejora propuesta, ver 07-bd-07 §3): `identity`, `catalog`, `ticketing`, `commerce`, `parking`, `ops`. Los FKs cruzan schemas sin costo; cada schema espeja un módulo del monolito y delimita una futura extracción a microservicio.

```mermaid
erDiagram
    %% ============ identity ============
    users ||--o{ user_roles : "tiene"
    roles ||--o{ user_roles : ""
    users ||--o{ refresh_tokens : "sesiones"
    users ||--o{ user_devices : "dispositivos FCM"
    users ||--o{ staff_assignments : "es staff en"

    %% ============ catalog ============
    users ||--o{ events : "organiza"
    categories ||--o{ events : "clasifica"
    events ||--|| event_policies : "1:1 configura"
    events ||--o{ event_zones : "zonas"
    events ||--o{ sponsor_events : ""
    sponsors ||--o{ sponsor_events : ""
    events ||--o{ staff_assignments : "asigna"

    %% ============ ticketing ============
    events ||--o{ ticket_types : "vende tarifas"
    event_zones ||--o{ ticket_types : "ubica (opcional)"
    ticket_types ||--o{ tickets : "instancia (FK compuesta con event_id)"
    users ||--o{ tickets : "propietario actual (1 columna)"
    order_items ||--o{ tickets : "origina (source_order_item_id)"
    tickets ||--o{ dynamic_qrs : "QR (1 vigente, parcial)"
    parking_reservations ||--o{ dynamic_qrs : "QR parking (1 vigente)"
    tickets ||--o{ ticket_history : "append-only"

    %% ============ commerce ============
    users ||--o{ orders : "compra"
    orders ||--o{ order_items : "compone"
    orders ||--o{ payments : "intentos (1 APPROVED)"
    ticket_types ||--o{ order_items : "ítem TICKET"
    parkings ||--o{ order_items : "ítem PARKING"
    temporal_reservations ||--o| order_items : "ítem EXCHANGE_TICKET"
    tickets ||--o{ exchange_listings : "publica (1 activa)"
    exchange_listings ||--o{ temporal_reservations : "reserva (1 ACTIVE)"
    exchange_listings ||--o| ticket_transfers : "concreta"
    tickets ||--o{ ticket_transfers : "historial de dueños"
    events ||--o{ waitlist_entries : "FIFO queue_seq"
    users ||--o{ waitlist_entries : ""
    waitlist_entries ||--o{ waitlist_offers : "ofertas (1 OFFERED)"
    ticket_types ||--o{ waitlist_offers : "fuente INVENTORY (cupo, C1)"
    exchange_listings ||--o{ waitlist_offers : "fuente EXCHANGE (WAITLIST_HOLD, C1)"
    tickets ||--o{ refund_requests : "reembolso (1 REQUESTED)"
    orders ||--o{ ledger_entries : "asientos (ref débil)"

    %% ============ parking ============
    events ||--o{ parkings : "dispone"
    parkings ||--o{ parking_slots : "plazas"
    parking_slots ||--o{ parking_reservations : "reserva (1 vigente)"
    order_items ||--o| parking_reservations : "origina"
    users ||--o{ parking_reservations : ""

    %% ============ checkins / engagement / ops ============
    tickets ||--o{ event_checkins : "1 GRANTED por ticket"
    dynamic_qrs ||--o{ event_checkins : "QR usado"
    parking_reservations ||--o{ parking_checkins : "entradas/salidas"
    users ||--o{ favorites : ""
    events ||--o{ favorites : ""
    users ||--o{ notifications : "recibe"
    users ||--o{ idempotency_keys : "scope por usuario"
```

## Distribución tabla → schema

| Schema | Tablas |
|---|---|
| `identity` | users, roles, user_roles, refresh_tokens, user_devices, staff_assignments |
| `catalog` | categories, events, event_policies, event_zones, sponsors, sponsor_events |
| `ticketing` | ticket_types, tickets, ticket_history, dynamic_qrs |
| `commerce` | orders, order_items, payments, ledger_entries, exchange_listings, temporal_reservations, ticket_transfers, waitlist_entries, waitlist_offers, refund_requests |
| `parking` | parkings, parking_slots, parking_reservations |
| `ops` | event_checkins, parking_checkins, favorites, notifications, global_config, idempotency_keys, outbox_events, audit_log |

Tablas append-only (sin `UPDATE`/`DELETE`, revocado a nivel de privilegios): `ticket_history`, `ticket_transfers`, `ledger_entries`, `event_checkins`, `parking_checkins`, `audit_log`.
