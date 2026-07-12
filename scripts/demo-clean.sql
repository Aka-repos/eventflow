-- EventFlow · Limpieza del Demo Seed (idempotente; solo borra datos demo-*)
-- Uso: docker run --rm -i -e PGPASSWORD="$DB_PASSWORD" postgres:17 \
--        psql "host=aws-0-us-east-1.pooler.supabase.com port=5432 dbname=postgres user=$DB_USER sslmode=require" \
--        < scripts/demo-clean.sql
BEGIN;

-- favoritos y outbox de los eventos demo
DELETE FROM ops.favorites WHERE event_id IN (
    SELECT e.id FROM catalog.events e
    JOIN identity.users u ON u.id = e.organizer_id
    WHERE u.email = 'demo-organizer@eventflow.dev');
DELETE FROM ops.outbox_events WHERE aggregate_id IN (
    SELECT e.id FROM catalog.events e
    JOIN identity.users u ON u.id = e.organizer_id
    WHERE u.email = 'demo-organizer@eventflow.dev');

-- catálogo demo (orden por FKs)
DELETE FROM catalog.sponsor_events WHERE event_id IN (
    SELECT e.id FROM catalog.events e
    JOIN identity.users u ON u.id = e.organizer_id
    WHERE u.email = 'demo-organizer@eventflow.dev');
DELETE FROM ticketing.ticket_types WHERE event_id IN (
    SELECT e.id FROM catalog.events e
    JOIN identity.users u ON u.id = e.organizer_id
    WHERE u.email = 'demo-organizer@eventflow.dev');
DELETE FROM catalog.event_zones WHERE event_id IN (
    SELECT e.id FROM catalog.events e
    JOIN identity.users u ON u.id = e.organizer_id
    WHERE u.email = 'demo-organizer@eventflow.dev');
DELETE FROM catalog.event_policies WHERE event_id IN (
    SELECT e.id FROM catalog.events e
    JOIN identity.users u ON u.id = e.organizer_id
    WHERE u.email = 'demo-organizer@eventflow.dev');
DELETE FROM catalog.events WHERE organizer_id IN (
    SELECT id FROM identity.users WHERE email = 'demo-organizer@eventflow.dev');
DELETE FROM catalog.sponsors WHERE name IN ('Acme Bebidas', 'Global Bank', 'Nimbus Telecom');
DELETE FROM catalog.categories WHERE name IN ('Conciertos', 'Deportes', 'Teatro', 'Tecnología')
    AND NOT EXISTS (SELECT 1 FROM catalog.events e WHERE e.category_id = catalog.categories.id);

-- usuarios demo
DELETE FROM identity.refresh_tokens WHERE user_id IN (
    SELECT id FROM identity.users WHERE email LIKE 'demo-%@eventflow.dev');
DELETE FROM identity.user_roles WHERE user_id IN (
    SELECT id FROM identity.users WHERE email LIKE 'demo-%@eventflow.dev');
DELETE FROM identity.users WHERE email LIKE 'demo-%@eventflow.dev';

COMMIT;
