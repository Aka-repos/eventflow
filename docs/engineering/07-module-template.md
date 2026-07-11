# EventFlow Engineering — 07. Plantilla Oficial de Módulo

> Todo módulo (existente al implementarse, o nuevo aprobado por gobernanza) **MUST** seguir exactamente esta plantilla. Se usa `exchange` como ejemplo ilustrativo de nombres; no es código.

## 1. Estructura backend

```text
backend/src/main/java/com/eventflow/modules/exchange/
├── domain/
│   ├── ExchangeListing.java              # agregado: estado + invariantes + transiciones
│   ├── ListingStatus.java                # enum con transiciones válidas
│   ├── ExchangePricing.java              # servicio/VO de dominio (cálculo con redondeo M1)
│   ├── ExchangeListingRepository.java    # PUERTO (interfaz)
│   ├── exception/                        # ListingNotAvailableException, … (1:1 con ErrorCode)
│   └── event/                            # ListingReserved, TicketTransferred, … (records)
├── application/
│   ├── PublishTicketUseCase.java         # @Transactional; 1 operación de negocio c/u
│   ├── ReserveListingUseCase.java
│   ├── CompleteTransferUseCase.java
│   ├── command/                          # ReserveListingCommand, … (records de entrada)
│   └── ExchangeFacade.java               # ÚNICA superficie visible para otros módulos
├── infrastructure/
│   ├── persistence/                      # JpaExchangeListingRepository (adapter del puerto) + PersistenceMapper
│   ├── scheduler/                        # ListingExpirationJob (expira por expires_at, idempotente)
│   └── consumer/                         # consumidores outbox del módulo (si aplica)
└── api/
    ├── ExchangeListingController.java    # operationIds del OpenAPI, tag exchange
    ├── dto/                              # espejo exacto de components.schemas
    └── ExchangeApiMapper.java
```

```text
backend/src/test/java/com/eventflow/modules/exchange/   # espejo exacto de paquetes
├── domain/…Test           # transiciones válidas E inválidas, pricing
├── application/…Test      # use cases con puertos mockeados
├── api/…IT                # MockMvc: contrato, seguridad (401/403/404), Problems
├── infrastructure/…IT     # Testcontainers: repos, locks, outbox, scheduler, concurrencia
└── ExchangeArchTest       # ArchUnit: reglas del doc 10 para este módulo
testFixtures/…             # aListing(), aReservedListing() — builders
```

## 2. Piezas obligatorias por módulo (checklist de esqueleto)

| Pieza | Regla |
|---|---|
| Agregados + máquina de estados | espejo exacto de design/04 §3; transición inválida lanza excepción tipada |
| Puertos de repositorio | en `domain`; adapter JPA en `infrastructure/persistence` |
| Use cases | uno por operación del OpenAPI del módulo; `@Transactional`; autorización por recurso al inicio |
| Fachada de application | lo ÚNICO que otro módulo puede invocar (doc 10) |
| Domain events | los del catálogo api/08 que el módulo emite, al outbox en la misma TX |
| Controller + DTOs + mapper | tag OpenAPI completo del módulo; envelope `{data}`; sin lógica |
| Excepciones ↔ ErrorCode | tabla en el README del módulo; registradas en api/02 si son nuevas |
| Scheduler jobs | solo si el módulo tiene `expires_at`; idempotentes; orden canónico de locks |
| Config | propiedades del módulo bajo prefijo `eventflow.<modulo>.*`, tipadas |
| Flyway | las tablas ya existen (V1–V9); cambios nuevos = migración aditiva propia del módulo |
| Tests | los 5 grupos del árbol de test + fixtures; cobertura doc 04 |
| README del módulo | 1 página: responsabilidad, eventos que emite/consume, dependencias (según doc 10), mapa excepción→ErrorCode |

## 3. Estructura Android del feature espejo

```text
app/src/main/java/com/app/eventflow/
├── domain/model/exchange/            # modelos de dominio del feature
├── domain/repository/ExchangeRepository.kt
├── domain/usecase/exchange/          # ReserveListingUseCase, …
├── data/remote/api/ExchangeApi.kt    # métodos = operationId del tag
├── data/remote/dto/exchange/         # espejo de schemas (congelados)
├── data/local/{dao,entity}/exchange/ # solo lo que la matriz offline exige cachear
├── data/repository/ExchangeRepositoryImpl.kt
├── data/mapper/ExchangeMappers.kt    # Dto→Entity→Domain; enums → UNKNOWN
└── ui/feature/exchange/
    ├── ExchangeNavGraph.kt           # rutas tipadas del feature
    ├── marketplace/                  # pantalla = Screen.kt + ViewModel.kt + UiState/UiEvent/UiEffect.kt
    └── mylistings/
```

Tests espejo: ViewModels (Turbine), repositorio (MockWebServer + fake DAO), DAO (Room in-memory), UI states del feature (Compose test).

## 4. Orden de implementación dentro de un módulo (obligatorio)

1. Leer contrato (OpenAPI del tag) + reglas (design) + dependencias (doc 10). Verificar DoR (doc 01 §6).
2. **Dominio con TDD**: agregado, estados, excepciones — sin Spring.
3. Puertos + use cases con TDD (puertos mockeados).
4. Infrastructure: adapters JPA + ITs con Testcontainers (locks/concurrencia incluidos).
5. API: controller + DTOs + mapper + ITs de contrato y seguridad.
6. Eventos: emisión al outbox + consumidores + ITs.
7. Scheduler del módulo (si aplica) + ITs de expiración.
8. Android feature espejo (misma secuencia: domain → data → ui).
9. DoD completo (doc 08) + checklist de PR (doc 11).

**MUST NOT** invertirse el orden empezando por controllers o pantallas ("API-first no es controller-first: el contrato ya existe; el dominio se construye primero").
