# EventFlow Engineering — 10. Reglas de Dependencias entre Módulos

## 1. Principios

1. Comunicación entre módulos SOLO por: (a) **fachada de application** del módulo destino (síncrono, misma TX cuando el caso de uso lo exige), o (b) **domain events vía outbox** (asíncrono).
2. **MUST NOT** importarse `domain`, `infrastructure` ni `api` de otro módulo. Ni repositorios ni entidades ajenas, jamás.
3. Las FKs de base de datos entre schemas NO autorizan dependencia de código (la FK `tickets → order_items` no permite que `ticketing` importe `ordering`).
4. `shared` no depende de ningún módulo; todos pueden depender de `shared`.
5. `audit`, `notifications`, `analytics` son **consumidores terminales**: nadie los invoca y ellos no invocan fachadas de negocio (solo leen sus datos y el outbox).

## 2. Matriz módulo → módulo

`S` = síncrono permitido (fachada application) · `E` = solo eventos (consume del emisor) · `—` = prohibido.
Filas = quién llama/consume; columnas = a quién. Todos → `shared` y lectura de `platformconfig` (puerto) están permitidos y se omiten.

| ↓ llama a → | identity | catalog | ticketing | ordering | payments | exchange | waitlist | refunds | parking | checkin | ledger |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **identity** | · | — | — | — | — | — | — | — | — | — | — |
| **catalog** | S¹ | · | — | — | — | — | — | — | — | — | — |
| **ticketing** | S¹ | S² | · | — | — | — | — | — | — | — | — |
| **ordering** | S¹ | S | S | · | S | S³ | — | — | S | — | S |
| **payments** | — | — | — | — | · | — | — | — | — | — | — |
| **exchange** | S¹ | S² | S | — | — | · | — | — | — | — | S |
| **waitlist** | S¹ | S² | E | S⁴ | — | S⁵ | · | E | — | — | — |
| **refunds** | S¹ | S² | S | S⁶ | S | — | — | · | — | — | S |
| **parking** | S¹ | S | — | — | — | — | — | — | · | — | — |
| **checkin** | S⁷ | S | S | — | — | — | — | — | S | · | — |
| **ledger** | — | — | — | — | — | — | — | — | — | — | · |
| **notifications** | S⁸ | E | E | E | E | E | E | E | E | E | — |
| **analytics** | — | E | E | E | E | E | E | E | E | E | S⁹ |
| **audit** | — | E | E | E | E | E | E | E | E | E | — |

Notas: ¹ solo resolución de usuarios (existencia/estado) · ² lectura de evento/política (snapshot) · ³ validar/consumir reserva temporal al pagar · ⁴ crear orden prioritaria al aceptar oferta · ⁵ transicionar listing WAITLIST_HOLD→PUBLISHED/SOLD · ⁶ localizar pago de adquisición (C2) · ⁷ verificar staff_assignment · ⁸ dispositivos FCM del usuario · ⁹ lectura del ledger para dashboards.

**Eventos clave:** `waitlist` consume `TicketReleased` (de refunds/exchange/ticketing); `ticketing` emite todos los de boleto/QR; `audit` consume TODO el catálogo api/08.

## 3. Reglas de importación (verificadas por ArchUnit en CI)

- `com.eventflow.modules.A..` **MUST NOT** importar `com.eventflow.modules.B.domain..`, `..B.infrastructure..` ni `..B.api..` (para todo A ≠ B).
- Importar `..B.application..` **MUST** limitarse a la fachada y sus commands/resultados públicos, y SOLO si la matriz marca `S`.
- `..domain..` **MUST NOT** importar `org.springframework..` (excepción única: anotaciones JPA en agregados, decisión pragmática del diseño design/03 §2.2).
- `..api..` **MUST NOT** importar `..infrastructure..`; `..infrastructure..` **MUST NOT** importar `..api..`.
- `com.eventflow.shared..` **MUST NOT** importar `com.eventflow.modules..`.
- Ciclos entre módulos (a nivel de paquete raíz de módulo) **MUST NOT** existir — la matriz es acíclica por construcción.

## 4. Reglas de visibilidad

- Público de un módulo: fachada de application + commands/queries/resultados + eventos de dominio (records del paquete `domain.event` son el contrato de integración).
- Todo lo demás **SHOULD** ser package-private; los adapters SIEMPRE.
- Android espejo: `ui.feature.A` **MUST NOT** importar `ui.feature.B` (composición solo vía navegación); `domain` no importa `data` ni `ui`.

## 5. Ejemplos

**Válidos**
1. `ordering.application.PayOrderUseCase` invoca `TicketingFacade.issueTickets(cmd)` — S en matriz, misma TX de confirmación.
2. `waitlist.infrastructure.consumer.TicketReleasedConsumer` procesa el evento `TicketReleased` del outbox — E en matriz.
3. `exchange.domain.ExchangePricing` usa `shared.domain.Money` — shared es libre.
4. `analytics` consulta `LedgerFacade.revenueByEvent(query)` — S⁹ de lectura.

**Inválidos (rechazo automático)**
1. `exchange` importa `ticketing.domain.Ticket` para cambiarle el owner — cruce a domain ajeno; **MUST** pedirse a `TicketingFacade.transferOwnership(cmd)`.
2. `ordering` inyecta `JpaTicketRepository` de ticketing — repositorio ajeno prohibido siempre.
3. `notifications` llama a `OrderingFacade` para reenviar un recibo — consumidor terminal; **MUST** reaccionar a eventos.
4. `refunds` publica directamente en `exchange` un listing al aprobar — la liberación viaja como `TicketReleased`; quien orquesta la prioridad es `waitlist`.
5. `checkin` valida la firma del QR localmente reimplementando JWS — la verificación es capacidad de `ticketing` (fachada) + `shared.security`.
6. `app: ui.feature.orders` importa `data.remote.dto.OrderDto` — la UI solo ve dominio.

## 6. Evolución de la matriz

Agregar una celda `S` nueva o invertir una dirección **MUST** pasar por ADR (doc 09 clase 3) y actualizar los tests ArchUnit en la misma PR. Agregar consumo `E` de un evento ya existente es cambio libre (documentado en el README del módulo).
