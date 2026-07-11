# EventFlow Engineering — 03. Guidelines Android (Kotlin + Compose)

> Complementa el contrato api/09 (normativo para red/offline) y la estructura design/03 §3. Aquí se fijan las reglas de implementación de UI y datos.

## 1. Patrón de presentación (MVVM + UDF)

Cada pantalla **MUST** definir exactamente esta tríada:

| Pieza | Regla |
|---|---|
| **UiState** | `data class <Pantalla>UiState` inmutable, con defaults; expuesta como `StateFlow` (`stateIn` con `WhileSubscribed(5s)`). **MUST** modelar explícitamente `Loading / Content / Error / Empty` (sealed o flags tipados — elegir sealed cuando los estados son excluyentes). |
| **UiEvent** | `sealed interface <Pantalla>UiEvent` — intenciones del usuario hacia el ViewModel (`onRetry`, `onPayClicked`). La UI **MUST NOT** llamar métodos arbitrarios del ViewModel: solo `onEvent(event)`. |
| **UiEffect** | `sealed interface <Pantalla>UiEffect` — efectos one-shot (navegar, snackbar) emitidos por `Channel`/`receiveAsFlow`. **MUST NOT** modelarse efectos como estado (evita re-emisión en recomposición). |

- ViewModels **MUST NOT** contener reglas de negocio (van en use cases/domino o en el servidor), referenciar clases de Android UI (Context de Activity, Views) ni DTOs/Entities — solo modelos de dominio.
- Corrutinas: **MUST** usarse `viewModelScope`; **MUST NOT** usarse `GlobalScope` ni `runBlocking` en producción. Dispatchers **MUST** inyectarse (testabilidad).

## 2. Compose

- Composables de pantalla **MUST** ser tontos: reciben `UiState` + lambda `onEvent`; sin acceso a ViewModel salvo el composable raíz de la ruta.
- **MUST** ser stateless por defecto (state hoisting); `remember` solo para estado efímero de UI (scroll, foco).
- Listas **MUST** usar `LazyColumn/LazyGrid` con `key` estable (id del dominio).
- **MUST NOT**: lógica de formateo de dinero/fechas inline (usar formatters del design system con `timezone` del evento — ADR-17/A5), `!!`, capturar estado mutable compartido en lambdas de composición.
- Previews **SHOULD** existir para cada componente del design system y cada estado principal de pantalla.

## 3. Navigation (Navigation Compose)

- Rutas **MUST** ser tipadas y centralizadas en `ui/navigation` (grafos por rol: attendee, organizer, admin, staff).
- Argumentos de ruta **MUST** ser IDs (UUID string), nunca objetos serializados.
- Deep links de notificaciones **MUST** mapearse desde `payload` (api/09 O12: refresh dirigido antes de navegar).
- La navegación **MUST** dispararse solo vía `UiEffect` — jamás dentro de composición.

## 4. Capa de datos

- **Repository** (interfaz en `domain/repository`): única fuente para los use cases. Impl **MUST** coordinar `RemoteDataSource` (Retrofit) + `LocalDataSource` (Room DAOs) — nunca exponer ninguno directamente.
- Lecturas **MUST** seguir stale-while-revalidate donde la matriz api/09 §2 lo indica: Flow desde Room + refresh en background; la UI **MUST NOT** leer de red directamente.
- Escrituras: la matriz offline api/09 §3 es **ley**. Compras/pagos/publicaciones/check-in **MUST NOT** encolarse jamás; solo favoritos usan cola WorkManager (server gana).
- Mappers `Dto → Entity → Domain` como funciones de extensión en `data/mapper`; enum desconocido → `UNKNOWN` SOLO en la frontera Dto→Domain.
- Room: entities espejo mínimo de lo que la UI necesita offline (no del esquema del servidor); migraciones de Room versionadas desde el día 1; **MUST NOT** almacenarse `qrToken` ni tokens de sesión en Room (tokens → EncryptedDataStore; QR → solo memoria).
- Retrofit: una interfaz por tag OpenAPI, métodos = `operationId`; **MUST NOT** construirse URLs a mano fuera de las interfaces.

## 5. Sincronización y conflictos

- `SyncManager` (WorkManager + ConnectivityObserver) **MUST** refrescar al recuperar red: boletos, órdenes activas, ofertas de waitlist y reservas (el estado pudo cambiar offline).
- Conflictos: **el servidor SIEMPRE gana** en datos de negocio; la copia local se sobrescribe. Para favoritos encolados, un `409/404` al sincronizar descarta la operación local silenciosamente.
- Cursors de paginación **MUST NOT** persistirse entre sesiones (api/07 §5).

## 6. Design System (`ui/theme` + `ui/components`)

- Material 3 con esquema de color, tipografía y shapes centralizados; **MUST NOT** hardcodearse colores/tamaños/espaciados en pantallas (tokens del theme).
- Componentes reutilizables obligatorios (única implementación permitida): `EfButton`, `EfTextField`, `EfCard`, `EfMoneyText` (formatea `Money`), `EfDateText` (formatea con timezone del evento), `EfCountdown` (QR/ofertas/reservas), `EfStatusChip` (mapea enums de estado a color semántico), `EfEmptyState`, `EfErrorState` (con retry), `EfLoading` (skeleton/indicator), `EfQrView`.
- Soporte **MUST**: modo claro y oscuro, strings 100% en `strings.xml` (es como base), contentDescription en elementos accionables, touch targets ≥ 48dp.

## 7. Manejo de errores, loading y empty

- Todo `AppError` (api/09 §1) **MUST** mapearse a UI: `Validation` → errores por campo; `Conflict/Business` → mensaje específico por `code` con acción (retry, refrescar, ir a waitlist); `Network` → estado offline con contenido cacheado si existe; `Auth` → logout + Login; `RateLimited` → mensaje con espera; `Unknown` → genérico + `traceId` visible en detalle.
- Pantallas de lista **MUST** distinguir: primera carga (skeleton) · refresh (indicador no bloqueante) · vacío (EfEmptyState con CTA) · error sin datos (EfErrorState) · error con datos (snackbar).
- `qr_not_yet_visible` **MUST** renderizarse como countdown (`EfCountdown` hasta `qrAvailableAt`), nunca como error.

## 8. Permitido / Prohibido (resumen ejecutable)

**Permitido:** Hilt, Retrofit+OkHttp, kotlinx.serialization, Room, WorkManager, DataStore (Encrypted), Coil (imágenes), CameraX + ML Kit (escáner), Maps Compose, Turbine/MockK/Robolectric en test.
**Prohibido sin ADR:** RxJava (solo corrutinas/Flow), LiveData (solo StateFlow), XML layouts/Fragments, EventBus, acceso directo a Supabase, librerías de UI de terceros que dupliquen M3, `SharedPreferences` plano para datos sensibles, lógica de precios/elegibilidad replicada en cliente (el servidor decide; la app solo renderiza `recovery-options` y precios recibidos).
