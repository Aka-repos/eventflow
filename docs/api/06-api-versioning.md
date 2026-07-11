# EventFlow API — 06. Estrategia de Evolución y Versionado

## 1. Principio

La API v1 es un **contrato congelado**: lo publicado no se rompe. Evoluciona por **adición**; solo un breaking change inevitable crea `/api/v2`.

## 2. Qué es cambio aditivo (permitido en v1, sin aviso)

- Agregar **endpoints** nuevos.
- Agregar **campos opcionales** a responses (el cliente ignora lo desconocido — tolerant reader).
- Agregar **query params opcionales** con default que preserva el comportamiento previo.
- Agregar **valores a enums** de *response* (por eso Android mapea desconocidos a `UNKNOWN`; ver §5).
- Agregar códigos al catálogo `ErrorCode` (los clientes tratan códigos desconocidos por familia HTTP).
- Relajar validaciones de request (aceptar más de lo que se aceptaba).

## 3. Qué es breaking change (requiere v2)

- Eliminar/renombrar endpoints, campos, headers o valores de enum.
- Cambiar tipo, formato, semántica u obligatoriedad de un campo existente.
- Endurecer validaciones de request (rechazar lo que antes se aceptaba).
- Cambiar códigos HTTP de casos ya documentados o el esquema de autenticación.
- Cambiar defaults de paginación/orden de forma observable.

**Excepción de seguridad:** una corrección de vulnerabilidad puede romper el contrato sin v2, con aviso inmediato (prioridad #1 del proyecto).

## 4. Proceso de deprecación

1. Anunciar en el changelog del contrato + marcar `deprecated: true` en OpenAPI.
2. Responder con headers: `Deprecation: true`, `Sunset: <fecha RFC 9557>`, `Link: <sucesor>; rel="successor-version"`.
3. Ventana mínima: **90 días** (MVP; 6 meses cuando haya terceros).
4. Tras el sunset: `410 Gone` con Problem `code: endpoint_retired` y puntero al sucesor.
5. Máximo **2 versiones mayores activas** (actual + anterior).

## 5. Evolución de DTOs — reglas para ambos lados

| Regla | Backend | Android |
|---|---|---|
| Campo response nuevo | siempre opcional u obligatorio-con-default en el JSON | deserialización tolerante (`ignoreUnknownKeys`) |
| Enum nuevo en response | permitido | todo enum del contrato se mapea con fallback `UNKNOWN` y la UI lo maneja genéricamente |
| Campo request nuevo | opcional con default server-side | puede enviarlo u omitirlo |
| Campo obsoleto | se mantiene sirviendo valor correcto hasta v2; se marca `deprecated: true` | deja de usarlo; no se elimina del DTO hasta v2 |
| Renombre necesario | se agrega el nuevo campo en paralelo (alias) y el viejo se depreca | migra al nuevo |

## 6. Gobernanza del contrato

- `05-openapi.yaml` es la **fuente única**: cambia primero el YAML (PR con revisión), después el código.
- CI valida: lint Redocly + **diff de compatibilidad** (`redocly diff` / oasdiff) que bloquea breaking changes accidentales en v1.
- El backend publica `/v3/api-docs` generado; un check compara lo servido contra el YAML congelado (drift = fallo de build).
- `info.version` sigue SemVer del contrato: MAJOR = path (`v1`), MINOR = adiciones, PATCH = correcciones de documentación.
- Todo cambio queda en `docs/api/CHANGELOG.md` (crear en el primer cambio post-congelamiento).
