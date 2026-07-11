-- EventFlow · V1: schemas por módulo, extensiones y rol de aplicación
-- PostgreSQL 17 / Supabase. UUIDs v7 los genera la aplicación; gen_random_uuid() es fallback operativo.

CREATE SCHEMA IF NOT EXISTS extensions;
CREATE EXTENSION IF NOT EXISTS citext WITH SCHEMA extensions;

CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS catalog;
CREATE SCHEMA IF NOT EXISTS ticketing;
CREATE SCHEMA IF NOT EXISTS commerce;
CREATE SCHEMA IF NOT EXISTS parking;
CREATE SCHEMA IF NOT EXISTS ops;

COMMENT ON SCHEMA identity  IS 'Usuarios, roles, sesiones, staff (bounded context Identity)';
COMMENT ON SCHEMA catalog   IS 'Eventos, políticas, zonas, categorías, patrocinadores';
COMMENT ON SCHEMA ticketing IS 'Tarifas, boletos, QRs dinámicos, historial';
COMMENT ON SCHEMA commerce  IS 'Órdenes, pagos, ledger, exchange, waitlist, reembolsos';
COMMENT ON SCHEMA parking   IS 'Estacionamientos, plazas, reservas';
COMMENT ON SCHEMA ops       IS 'Check-ins, notificaciones, config, idempotencia, outbox, auditoría';

-- Rol de aplicación (NOLOGIN): el usuario LOGIN del backend se crea fuera de las
-- migraciones (secreto) y se le hace GRANT eventflow_app.
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'eventflow_app') THEN
    CREATE ROLE eventflow_app NOLOGIN;
  END IF;
END $$;
