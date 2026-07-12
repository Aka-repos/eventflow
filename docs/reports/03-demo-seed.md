# Informe 03 — Demo Seed oficial y entorno de demostración

- **Fecha:** 2026-07-11
- **Mecanismo:** `com.eventflow.demo.DemoSeeder` — `ApplicationRunner` de Spring activado **exclusivamente con el perfil `demo`** (`@Profile("demo")`); jamás corre en test/prod salvo activación explícita. **Idempotente**: cada entidad se busca por su clave natural (email/nombre/título) y solo se crea si falta — re-ejecutarlo N veces no duplica nada (verificado por `DemoSeedIT` con Testcontainers).
- **Sin tocar Flyway**: cero migraciones nuevas; los datos se crean por los **casos de uso reales** del Módulo 1 y 2 (mismas invariantes, misma emisión al outbox que producción). Únicas operaciones por SQL directo — porque el contrato v1 no las expone: promoción de roles (endpoint llega en módulo 10) y vinculación sponsor↔evento (mejora propuesta en informe 02 §7.2), ambas con `ON CONFLICT DO NOTHING`.

---

## 1. Datos creados (ya sembrados en Supabase)

**Usuarios (contraseña de todos: `Demo1234!`):**

| Email | Roles |
|---|---|
| `demo-admin@eventflow.dev` | ATTENDEE + **ADMIN** |
| `demo-organizer@eventflow.dev` | ATTENDEE + **ORGANIZER** (dueño de todos los eventos demo) |
| `demo-user@eventflow.dev` | ATTENDEE (para navegar/favoritos desde Android) |

**Categorías (4):** Conciertos, Deportes, Teatro, Tecnología.
**Sponsors (3):** Acme Bebidas, Global Bank, Nimbus Telecom (vinculados: Festival ← Acme+Nimbus; Clásico ← Global Bank).

**Eventos publicados (5), todos con política personalizada y outbox `EventPublished`:**

| Evento | Categoría | Zonas | Tarifas | Política |
|---|---|---|---|---|
| Festival Ritmo Urbano (en +14 días) | Conciertos | VIP 300 · General 2500 | VIP 120.00 · General 45.00 | exchange ON (10%), waitlist ON |
| Clásico Nacional de Fútbol (+21 d) | Deportes | Tribuna Norte 8000 · Palco 500 | Tribuna 18.00 · Palco 95.00 | waitlist ON |
| La Casa de Bernarda Alba (+7 d) | Teatro | — | Butaca 30.00 ×400 | básica |
| DevConf Panamá 2026 (+30 d) | Tecnología | Sala Principal 1200 | Completo 150.00 · Estudiante 60.00 | exchange ON (15%) |
| Noche Sinfónica bajo las Estrellas (+45 d) | Conciertos | — | Única 25.00 ×3000 | waitlist ON |

Las fechas son relativas al momento del seed (siempre futuras). Totales: 8 tarifas, 5 zonas, 5 políticas, 3 vínculos de sponsor.

## 2. Verificaciones realizadas

| Chequeo | Resultado |
|---|---|
| Backend arranca contra Supabase (perfil demo) | ✅ `Started EventFlowApplication` + Flyway `Successfully validated 9 migrations` |
| Conexión Supabase | ✅ (pooler sesión, TLS; seed escribió y leyó) |
| Swagger | ✅ `/swagger-ui` 200, `/v3/api-docs` 200 |
| Actuator | ✅ `/actuator/health` `{"status":"UP"}`; `/env` y demás → 401 (restringido) |
| Catálogo navegable | ✅ `GET /events` devuelve los 5 eventos con `priceFrom` correcto; búsqueda y filtros operativos |
| Login demo + favorito + detalle | ✅ `demo-user` login 200, favorito 204, detalle con `isFavorite=true`, 2 sponsors y 2 tarifas |
| APK debug | ✅ `app/build/outputs/apk/debug/app-debug.apk` (12.6 MB) |
| Suite backend con la IT del seed | ✅ **100/100** (la `DemoSeedIT` prueba población + re-ejecución sin duplicados) |

## 3. Cómo volver a ejecutar el Demo Seed

```bash
set -a; source .env; set +a
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
SPRING_PROFILES_ACTIVE=demo ./gradlew -p backend bootRun
```

El seed corre en cada arranque con perfil `demo` y **no duplica nada** si los datos ya existen (verás `demo_seed_done` en el log, sin líneas "creado"). Sin el perfil (`bootRun` normal), el seeder ni se instancia.

## 4. Cómo limpiar los datos

```bash
set -a; source .env; set +a
docker run --rm -i -e PGPASSWORD="$DB_PASSWORD" postgres:17 \
  psql "host=aws-0-us-east-1.pooler.supabase.com port=5432 dbname=postgres user=$DB_USER sslmode=require" \
  < scripts/demo-clean.sql
```

[scripts/demo-clean.sql](../../scripts/demo-clean.sql) borra **solo** lo demo (usuarios `demo-*@eventflow.dev`, sus eventos/zonas/tarifas/políticas/favoritos/outbox, sponsors y categorías demo sin uso), en una transacción y respetando FKs. Es seguro re-ejecutarlo.

## 5. Preparar el entorno para una demostración futura

1. (Opcional) limpiar: paso §4.
2. Levantar backend con seed: paso §3 — deja Supabase poblado y la API sirviendo en `http://localhost:8080/api/v1`.
3. Instalar la app en el emulador Pixel_9:
   ```bash
   ~/Library/Android/sdk/emulator/emulator -avd Pixel_9 &
   ~/Library/Android/sdk/platform-tools/adb wait-for-device
   ~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
4. En la app: registrarse con un email nuevo **o** iniciar sesión como `demo-user@eventflow.dev` / `Demo1234!` → Explorar (5 eventos, búsqueda, filtros por categoría) → detalle con entradas/políticas/sponsors → corazón para favoritos (funciona offline; se sincroniza al volver la red).

**Estado actual:** el backend quedó **corriendo** con el catálogo demo sembrado en Supabase — puedes instalar el APK y navegar ya. Si se detiene (reinicio de la máquina, etc.), basta el paso §3.

## 6. Nota de gobernanza

El seeder vive en `com.eventflow.demo` (fuera de `modules/`): es tooling de desarrollo, no un módulo de negocio; consume únicamente fachadas/casos de uso públicos de application (las reglas ArchUnit de la matriz siguen en verde, 100/100). No introduce funcionalidad del Módulo 3.
