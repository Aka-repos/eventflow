-- EventFlow · V9: datos semilla (idempotente)

INSERT INTO identity.roles (id, code) VALUES
    (1, 'ADMIN'),
    (2, 'ORGANIZER'),
    (3, 'STAFF'),
    (4, 'ATTENDEE')
ON CONFLICT (id) DO NOTHING;

-- Configuración global inicial (ADR-14/políticas; editable desde el panel admin sin cambios de código)
INSERT INTO ops.global_config (key, value, description) VALUES
    ('exchange.fee_pct',
     '{"pct": 5}',
     'Comisión de EventFlow sobre ventas del Official Ticket Exchange (%). Solo se cobra en transferencias exitosas.'),
    ('payments.providers',
     '{"enabled": ["FAKE"]}',
     'Proveedores de pago habilitados. FAKE = proveedor simulado para desarrollo/demo.'),
    ('defaults.temp_reservation_minutes',
     '{"minutes": 10}',
     'Duración por defecto de la reserva temporal en el Exchange si el evento no la define.'),
    ('defaults.waitlist_offer_minutes',
     '{"minutes": 15}',
     'Ventana por defecto para aceptar una oferta de waitlist si el evento no la define.'),
    ('defaults.order_expiration_minutes',
     '{"minutes": 15}',
     'Ventana para pagar una orden PENDING antes de liberar inventario.'),
    ('idempotency.ttl_hours',
     '{"hours": 48}',
     'TTL de las claves de idempotencia antes del barrido de limpieza.')
ON CONFLICT (key) DO NOTHING;
