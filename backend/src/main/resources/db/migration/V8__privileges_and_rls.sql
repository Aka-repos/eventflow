-- EventFlow · V8: privilegios mínimos, inmutabilidad física y RLS
-- Capa 4 de la defensa en profundidad (07-bd-06 §9).

-- ===== Grants base para el rol de aplicación =====
GRANT USAGE ON SCHEMA identity, catalog, ticketing, commerce, parking, ops, extensions TO eventflow_app;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES    IN SCHEMA identity, catalog, ticketing, commerce, parking, ops TO eventflow_app;
GRANT USAGE, SELECT                  ON ALL SEQUENCES IN SCHEMA identity, catalog, ticketing, commerce, parking, ops TO eventflow_app;

-- Migraciones futuras heredan los grants
ALTER DEFAULT PRIVILEGES IN SCHEMA identity, catalog, ticketing, commerce, parking, ops
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO eventflow_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA identity, catalog, ticketing, commerce, parking, ops
    GRANT USAGE, SELECT ON SEQUENCES TO eventflow_app;

-- ===== Inmutabilidad física de las tablas append-only =====
REVOKE UPDATE, DELETE ON ticketing.ticket_history   FROM eventflow_app;
REVOKE UPDATE, DELETE ON commerce.ticket_transfers  FROM eventflow_app;
REVOKE UPDATE, DELETE ON commerce.ledger_entries    FROM eventflow_app;
REVOKE UPDATE, DELETE ON ops.event_checkins         FROM eventflow_app;
REVOKE UPDATE, DELETE ON ops.parking_checkins       FROM eventflow_app;
REVOKE UPDATE, DELETE ON ops.audit_log              FROM eventflow_app;
REVOKE TRUNCATE ON ALL TABLES IN SCHEMA identity, catalog, ticketing, commerce, parking, ops FROM eventflow_app;

-- ===== Bloqueo de los roles expuestos de Supabase (si existen) =====
DO $$
DECLARE r TEXT;
BEGIN
  FOREACH r IN ARRAY ARRAY['anon','authenticated'] LOOP
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = r) THEN
      EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA identity, catalog, ticketing, commerce, parking, ops FROM %I', r);
      EXECUTE format('REVOKE USAGE ON SCHEMA identity, catalog, ticketing, commerce, parking, ops FROM %I', r);
    END IF;
  END LOOP;
END $$;

-- ===== RLS deny-by-default + acceso pleno solo para eventflow_app =====
-- La app nunca consulta Supabase directo (PostgREST no expone estos schemas); RLS es el cinturón extra.
DO $$
DECLARE t RECORD;
BEGIN
  FOR t IN
    SELECT schemaname, tablename FROM pg_tables
    WHERE schemaname IN ('identity','catalog','ticketing','commerce','parking','ops')
  LOOP
    EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', t.schemaname, t.tablename);
    EXECUTE format(
      'CREATE POLICY app_full_access ON %I.%I FOR ALL TO eventflow_app USING (true) WITH CHECK (true)',
      t.schemaname, t.tablename);
  END LOOP;
END $$;
