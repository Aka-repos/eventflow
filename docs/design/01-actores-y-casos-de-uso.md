# EventFlow — Actores y Casos de Uso (UML)

## 1. Actores del sistema

### Actores humanos (primarios)

| Actor | Descripción | Hereda de |
|-------|-------------|-----------|
| **Visitante** | Usuario no autenticado. Solo puede explorar eventos públicos y registrarse. | — |
| **Asistente** | Usuario autenticado que compra boletos, reserva parking, usa el Exchange y la Waitlist. | Visitante |
| **Organizador** | Crea y administra eventos, políticas, boletos, parkings, zonas; ve estadísticas e ingresos; aprueba reembolsos. | Asistente |
| **Staff de Acceso** | Personal de puerta/parking asignado por un organizador a un evento. Permiso mínimo: escanear y validar QR de ese evento. *(Mejora — ADR-13)* | — |
| **Administrador** | Administra la plataforma: usuarios, organizadores, categorías, patrocinadores, configuración global, reportes. | — |

### Actores de sistema (secundarios)

| Actor | Rol |
|-------|-----|
| **Proveedor de Pagos** | Procesa cobros y devoluciones (Stripe/PayPal/Yappy/simulado, tras el puerto `PaymentProvider`). |
| **Proveedor de Notificaciones** | Push (FCM) / email. |
| **Google Maps** | Mapas, rutas y geolocalización en la app. |
| **Scheduler (Reloj)** | Dispara expiraciones: reservas temporales, ofertas de waitlist, publicaciones, QRs, transiciones de estado del evento. |

## 2. Diagrama de Casos de Uso (PlantUML)

> Renderizar con PlantUML (plugin de IntelliJ/VSCode o https://plantuml.com). Se agrupa por módulo para legibilidad; las relaciones `<<include>>`/`<<extend>>` marcan los flujos compuestos.

```plantuml
@startuml EventFlow-CasosDeUso
left to right direction
skinparam packageStyle rectangle
skinparam actorStyle awesome

actor Visitante as V
actor Asistente as A
actor Organizador as O
actor "Staff de Acceso" as S
actor Administrador as ADM
actor "Proveedor de Pagos" as PAY <<sistema>>
actor "Proveedor de Notificaciones" as NOTIF <<sistema>>
actor "Scheduler" as CLK <<sistema>>
actor "Google Maps" as MAPS <<sistema>>

V <|-- A
A <|-- O

package "Identidad y Acceso" {
  usecase "Registrarse" as UC01
  usecase "Iniciar sesión (JWT)" as UC02
  usecase "Refrescar token" as UC03
  usecase "Cerrar sesión" as UC04
  usecase "Gestionar perfil" as UC05
}

package "Catálogo de Eventos" {
  usecase "Buscar / filtrar eventos" as UC10
  usecase "Ver detalle del evento" as UC11
  usecase "Ver mapa (evento, parkings, rutas)" as UC12
  usecase "Guardar favorito" as UC13
  usecase "Ver agenda e historial" as UC14
}

package "Compra (Órdenes)" {
  usecase "Crear orden de compra" as UC20
  usecase "Pagar orden" as UC21
  usecase "Comprar boletos" as UC22
  usecase "Reservar parking" as UC23
  usecase "Ver mis boletos" as UC24
  usecase "Ver QR dinámico\n(dentro de la ventana)" as UC25
}

package "Cancelación Inteligente" {
  usecase "Indicar 'no podré asistir'" as UC30
  usecase "Solicitar reembolso" as UC31
  usecase "Publicar en Official\nTicket Exchange" as UC32
  usecase "Cancelar publicación" as UC33
}

package "Official Ticket Exchange" {
  usecase "Explorar boletos publicados" as UC40
  usecase "Comprar boleto del Exchange" as UC41
  usecase "Reservar temporalmente boleto" as UC42
  usecase "Transferir propiedad\n(nuevo QR + historial)" as UC43
}

package "Lista de Espera" {
  usecase "Unirse a la waitlist" as UC50
  usecase "Aceptar oferta de boleto\n(ventana limitada)" as UC51
  usecase "Ofrecer boleto liberado\nal siguiente en FIFO" as UC52
}

package "Control de Acceso" {
  usecase "Escanear QR de entrada" as UC60
  usecase "Validar QR contra servidor" as UC61
  usecase "Check-in / check-out parking" as UC62
}

package "Gestión de Eventos (Organizador)" {
  usecase "Crear / editar / eliminar evento" as UC70
  usecase "Publicar evento" as UC71
  usecase "Configurar políticas del evento" as UC72
  usecase "Administrar tipos de boleto y zonas" as UC73
  usecase "Administrar parkings" as UC74
  usecase "Aprobar / rechazar reembolsos" as UC75
  usecase "Ver estadísticas e ingresos" as UC76
  usecase "Invalidar / reemitir boleto" as UC77
  usecase "Asignar staff de acceso" as UC78
}

package "Administración de Plataforma" {
  usecase "Dashboard global y reportes" as UC80
  usecase "CRUD usuarios y organizadores" as UC81
  usecase "Gestionar categorías" as UC82
  usecase "Gestionar patrocinadores" as UC83
  usecase "Configuración global\n(comisión, tiempos, proveedores)" as UC84
}

package "Procesos del Sistema" {
  usecase "Expirar reservas / ofertas / publicaciones" as UC90
  usecase "Enviar notificaciones" as UC91
  usecase "Registrar auditoría" as UC92
  usecase "Sincronizar datos offline" as UC93
}

' --- Visitante
V --> UC01
V --> UC10
V --> UC11

' --- Asistente
A --> UC02
A --> UC03
A --> UC04
A --> UC05
A --> UC12
A --> UC13
A --> UC14
A --> UC22
A --> UC23
A --> UC24
A --> UC25
A --> UC30
A --> UC33
A --> UC40
A --> UC41
A --> UC50
A --> UC51
A --> UC93

' --- Organizador
O --> UC70
O --> UC71
O --> UC72
O --> UC73
O --> UC74
O --> UC75
O --> UC76
O --> UC77
O --> UC78
O --> UC60

' --- Staff
S --> UC60
S --> UC62

' --- Administrador
ADM --> UC80
ADM --> UC81
ADM --> UC82
ADM --> UC83
ADM --> UC84

' --- Includes / Extends
UC22 ..> UC20 : <<include>>
UC23 ..> UC20 : <<include>>
UC41 ..> UC42 : <<include>>
UC41 ..> UC20 : <<include>>
UC20 ..> UC21 : <<include>>
UC21 ..> UC92 : <<include>>
UC30 ..> UC31 : <<extend>>\n(período de reembolso activo)
UC30 ..> UC32 : <<extend>>\n(período expirado y\nExchange habilitado)
UC43 ..> UC92 : <<include>>
UC60 ..> UC61 : <<include>>
UC62 ..> UC61 : <<include>>
UC52 ..> UC91 : <<include>>
UC31 ..> UC75 : <<include>>
UC10 ..> UC50 : <<extend>>\n(evento SOLD_OUT\ncon waitlist activa)

' --- Actores de sistema
UC21 --> PAY
UC31 --> PAY
UC41 --> PAY
UC91 --> NOTIF
UC12 --> MAPS
CLK --> UC90
UC90 ..> UC52 : <<include>>
UC43 ..> UC91 : <<include>>
@enduml
```
![alt text](image-1.png)
## 3. Notas de trazabilidad

- **UC30 (Cancelación inteligente)** es el punto de entrada único: el sistema evalúa `EventPolicy` + estado del boleto y ofrece **solo** la acción válida (reembolso *o* publicación, nunca ambas). Los `<<extend>>` reflejan esa exclusión mutua.
- **UC42 (Reserva temporal)** siempre precede a UC41: mientras exista una reserva activa, ningún otro usuario puede iniciar la compra del mismo boleto.
- **UC52 (Oferta a waitlist)** intercepta toda liberación de boleto (reembolso, cancelación, expiración de publicación) antes de que el boleto pueda publicarse en el Exchange.
- **UC61 (Validación server-side)** es obligatorio para todo escaneo: el dispositivo nunca decide por sí solo (prioridad de reglas #1 y #2).
- **UC90** materializa expiraciones basadas en `expires_at` (ADR-10); nunca es la única línea de defensa: cada lectura crítica también verifica expiración.

## 4. Matriz actor × módulo (resumen de permisos)

| Módulo | Visitante | Asistente | Staff | Organizador | Admin |
|---|---|---|---|---|---|
| Catálogo (lectura) | ✔ | ✔ | — | ✔ | ✔ |
| Órdenes / pagos | — | ✔ | — | ✔ (como comprador) | — |
| Exchange / Waitlist | — | ✔ | — | ✔ (config. por evento) | ✔ (comisión global) |
| Reembolsos | — | solicita | — | aprueba/rechaza | supervisa |
| Check-in | — | — | ✔ (evento asignado) | ✔ (sus eventos) | — |
| Gestión de eventos | — | — | — | ✔ (propios) | ✔ (todos) |
| Configuración global | — | — | — | — | ✔ |

Toda autorización se valida en el servidor con reglas por recurso (propiedad del boleto, evento del organizador, evento asignado al staff), no solo por rol.
