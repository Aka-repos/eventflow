# EventFlow Engineering — 06. Estándares de Código

## 1. Principios (aplicación normativa, no decorativa)

| Principio | Regla verificable en EventFlow |
|---|---|
| SRP | una clase = una razón de cambio; use case por operación de negocio; controller por recurso |
| OCP | nuevas políticas de evento entran por `extra_policies`/nuevas Policy classes, sin tocar el agregado Event |
| LSP | implementaciones de un puerto **MUST** ser intercambiables (FakePayment ↔ Stripe sin tocar application) |
| ISP | puertos pequeños por capacidad (`PaymentProviderPort`≠`NotificationPort`), no interfaces ómnibus |
| DIP | domain/application dependen de puertos; infrastructure implementa (hexagonal) |
| DDD | la regla vive en el agregado; servicios de dominio solo para lógica multi-agregado; el lenguaje del código = lenguaje del diseño (Ticket, Listing, WaitlistEntry — no "Item", "Post") |
| DRY | duplicar conocimiento prohibido; duplicar código trivial 2 veces **MAY**, a la 3ª se extrae |
| KISS/YAGNI | prohibido código especulativo: sin flags "por si acaso", sin abstracciones con un solo uso salvo puertos del diseño |

## 2. Límites cuantitativos (falla el review si se exceden sin justificación en la PR)

| Métrica | Límite |
|---|---|
| Método/función | ≤ 50 líneas; **SHOULD** ≤ 25 |
| Clase/archivo | ≤ 400 líneas típico; 800 máximo absoluto |
| Parámetros | ≤ 4 (más ⇒ Command/record o builder) |
| Complejidad ciclomática | ≤ 10 por método |
| Anidamiento | ≤ 4 niveles (early return obligatorio) |
| Composable | ≤ 100 líneas; extraer subcomposables |

## 3. Comentarios y documentación de código

- El código **MUST** autoexplicarse por nombres; comentario solo para restricción que el código no puede expresar (por qué, nunca qué). Comentario que narra la línea siguiente **MUST NOT** existir.
- JavaDoc/KDoc **MUST** en: fachadas públicas de application, puertos, agregados (invariantes y transiciones), y el shared kernel. **MUST NOT** exigirse en privados triviales, getters, DTOs espejo del contrato.
- `// TODO` **MUST** llevar issue (`TODO(#42):`); TODO huérfano = deuda crítica en review.
- Referencias a decisiones: comentario **MAY** citar ADR/documento (`// Orden canónico de locks: engineering/02 §6`).

## 4. Java (backend)

- Records **MUST** para DTOs, Commands/Queries y VOs inmutables; clases solo cuando hay comportamiento con estado controlado (agregados JPA).
- `Optional` **MUST** solo como retorno de búsquedas que pueden no hallar; **MUST NOT** en campos, parámetros ni colecciones.
- Streams **SHOULD** para transformaciones simples; **MUST NOT** anidarse streams complejos (extraer métodos); efectos secundarios dentro de streams prohibidos.
- Dinero **MUST** ser el VO `Money` (BigDecimal); aritmética monetaria fuera del VO prohibida (redondeo M1 vive ahí).
- Fechas **MUST** `Instant`/`OffsetDateTime` UTC; `LocalDateTime` prohibido salvo presentación explícita con zona.
- Inyección **MUST** por constructor; `@Autowired` en campos prohibido.
- Enums de estado **MUST** exponer sus transiciones válidas (la máquina de estados es del dominio, no un switch en el use case).

## 5. Kotlin (Android)

- `data class` para modelos/estados inmutables (`val` siempre; `var` en dominio/estado prohibido — actualizar con `copy`/`update`).
- `sealed interface/class` **MUST** para UiState/UiEvent/UiEffect, AppError y resultados con variantes cerradas.
- Corrutinas: structured concurrency obligatoria; cancelación cooperativa; `Flow` frío para streams de datos, `StateFlow` para estado, `Channel` para efectos; `suspend` en repositorios y use cases de una emisión.
- Null-safety: `!!` prohibido; `lateinit` solo en propiedades de framework inevitables.
- Enums del contrato **MUST** incluir `UNKNOWN` (doc api/06 §5) y mapearse en la frontera Dto→Domain.

## 6. Antipatrones prohibidos (lista cerrada de rechazo automático)

1. **Modelo anémico**: agregado como bolsa de getters/setters con la lógica en "services".
2. **God class / util dumping ground** (`Utils`, `Helper`, `Manager` genéricos).
3. **Fuga de capas**: entidad JPA/Room en controller/ViewModel/UI; DTO en dominio.
4. **Lógica de negocio en Controller o ViewModel** (elegibilidad, precios, transiciones).
5. **double/float para dinero**; aritmética monetaria fuera de `Money`.
6. **Catch-and-swallow** (excepción tragada o solo logueada) y `catch (Exception e)` genérico fuera del ControllerAdvice/dispatcher.
7. **Mutación compartida**: singletons con estado mutable, `GlobalScope`, colecciones mutables expuestas.
8. **Magic numbers/strings**: constantes con nombre o configuración; códigos de error inline en vez del catálogo.
9. **Boolean flag parameters** que bifurcan comportamiento (dividir el método).
10. **Feature envy / tell-don't-ask** invertido: use case leyendo campos del agregado para decidir lo que el agregado debe decidir.
11. **Setters públicos en agregados**: el estado cambia solo por métodos de negocio con nombre del dominio.
12. **SQL nativo ad-hoc** fuera de repositorios; concatenación de SQL prohibida siempre.
13. **Timers en memoria** como única defensa de expiraciones (viola ADR-10).
14. **Llamadas HTTP dentro de una transacción/lock** (viola A2 y §6 del doc 02).
15. **Lombok/MapStruct/RxJava/LiveData** (prohibidos sin ADR — docs 02 §5 y 03 §8).
16. **Tests con sleeps, orden implícito o asserts de texto de UI del servidor** (doc 04 §6).
17. **Replicar en el cliente decisiones del servidor** (precios, elegibilidad de reembolso, validez de QR).
18. **Copiar-pegar entre módulos** en lugar de subir al shared kernel (si es técnico) o exponer puerto (si es de negocio).
