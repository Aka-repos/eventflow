# EventFlow — Roadmap de Implementación por Módulos

> Orden diseñado para que cada módulo entregue valor verificable y los siguientes construyan sobre cimientos estables. TDD en todos (test primero, cobertura ≥80%): JUnit 5 + Mockito + Testcontainers (PostgreSQL) en backend; JUnit + Turbine + MockK y pruebas de UI Compose en Android.

| # | Módulo | Backend | Android | Depende de |
|---|--------|---------|---------|------------|
| 0 | **Fundaciones** | Proyecto Spring Boot, módulos `shared` (Money, errores, envelope, ProblemDetail), conexión Supabase, migraciones Flyway, OpenAPI, CI básico | Estructura de paquetes (doc 03), Hilt, Retrofit+OkHttp, Room, tema M3, navegación esqueleto | — |
| 1 | **Identidad y Auth** | Registro, login, JWT + refresh con rotación, roles, Spring Security, rate limit en `/auth` | Pantallas login/registro, EncryptedDataStore, AuthInterceptor + Authenticator, sesión en Room | 0 |
| 2 | **Catálogo** | CRUD eventos (organizador), EventPolicy, categorías, zonas, publicación, búsqueda/filtros | Lista/búsqueda/filtros, detalle de evento, favoritos offline, mapa (Google Maps) | 1 |
| 3 | **Órdenes + Pagos + Ticketing** | Order/OrderItem, FakePaymentProvider tras el puerto, emisión de tickets con snapshot de política, inventario con `FOR UPDATE`, idempotencia, ledger | Checkout, mis boletos (offline), historial de órdenes | 2 |
| 4 | **QR dinámicos + Check-in** | Emisión JWS, ventana de visibilidad, validación server-side, invalidación/reemisión, staff assignments | Pantalla QR con countdown y refresh, escáner (CameraX + ML Kit) para organizador/staff | 3 |
| 5 | **Reembolsos + Cancelación inteligente** | `recovery-options`, RefundRequest, aprobación del organizador, retorno a inventario, outbox→notificaciones | Flujo "no podré asistir" (solo opción válida), bandeja de aprobación del organizador | 3 |
| 6 | **Official Ticket Exchange** | Listings con precio calculado, reserva temporal + expiración, transferencia atómica (owner + QR + historial + comisión + ledger) | Explorar/publicar/comprar en Exchange, estados en tiempo real | 4, 5 |
| 7 | **Waitlist** | FIFO, ofertas con ventana, integración con liberaciones (reembolso/cancelación/expiración), prioridad sobre Exchange | Unirse/salir, oferta con cuenta regresiva, aceptar compra | 6 |
| 8 | **Parking** | Parkings, slots con máquina de estados, reserva vía orden, check-in/out | Reserva de parking en checkout, mapa de parkings, escáner de parking | 3, 4 |
| 9 | **Notificaciones** | Outbox dispatcher → FCM/email (puerto NotificationProvider), plantillas | Registro de dispositivo, centro de notificaciones, push | 3+ |
| 10 | **Dashboards y Admin** | Métricas desde ledger + vistas, reportes, CRUD admin, configuración global | Dashboard organizador (gráficas), panel admin | 3–9 |
| 11 | **Endurecimiento** | Auditoría E2E, pruebas de concurrencia (compras simultáneas), rate limiting global, revisión de seguridad | Sync offline completo, pulido UX, accesibilidad | todos |

## Criterios de salida por módulo

1. Tests en verde con cobertura ≥80% (unit + integración; E2E en flujos críticos).
2. Reglas de negocio del módulo demostradas con tests de dominio (transiciones inválidas rechazadas).
3. Endpoints documentados en OpenAPI y espejados en DTOs de Android.
4. Revisión de código + revisión de seguridad en módulos sensibles (auth, pagos, QR, exchange).
5. Sin `TODO` de integridad pendiente (índices/constraints del doc 04 aplicados en la migración del módulo).

## Primer paso propuesto

**Módulo 0 + 1**: fundaciones y autenticación completa (backend y app), porque todo lo demás depende de identidad, sesión y el pipeline de errores/idempotencia compartido.
