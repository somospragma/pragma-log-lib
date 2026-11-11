# Changelog

Todos los cambios relevantes de pragma-log. El formato sigue [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y las versiones siguen [versionado semántico](https://semver.org/lang/es/):

- **MAJOR** (`2.0.0`): cambios que obligan a tocar la app: una propiedad que se elimina o cambia de significado, un formato de salida distinto por defecto, una API pública que cambia.
- **MINOR** (`1.1.0`): funcionalidad nueva compatible: propiedades nuevas con un default que no cambia la salida actual.
- **PATCH** (`1.0.1`): correcciones y actualizaciones de dependencias sin cambios de configuración.

Cada release es un tag `vX.Y.Z` sobre la rama principal: el pipeline lo construye, pide aprobación y lo publica en Maven Central. Antes de crear el tag, mueve lo de `Sin publicar` a una sección con la versión y la fecha.

## [Sin publicar]

### Agregado

- Build: Gradle verifica el SHA-256 de cada dependencia y plugin contra `gradle/verification-metadata.xml`.

### Cambiado

- Build: la cobertura mínima por módulo sube de 75% a 80%, como el quality gate de SonarCloud, y la cobertura de los tests de `examples/quarkus` cuenta para los módulos de Quarkus.
- Quarkus: el interceptor de `@LogEnrichment` y la captura de bodies reciben sus dependencias por constructor.

### Corregido

- Spring Boot: dos recargas de configuración en caliente al mismo tiempo podían dejar un encoder sin detener. Ahora el cambio de encoder es atómico.

## [1.0.1] - 2026-11-11

### Cambiado

- Spring Boot (MVC y WebFlux): el `http.response.body` del log de fin de request es el objeto que retorna el `@RestController`, así que aplican sus `@MaskedLogField`, como en Quarkus. Antes solo se enmascaraba por nombre de campo y por patrones, y un campo anotado con un nombre que no estuviera en `masking.fields` salía en claro. Los textos, binarios y respuestas de funciones (`RouterFunction`) se siguen logueando como bytes.
- Spring Boot (MVC y WebFlux): cuando un request termina con una excepción no manejada (status 500 o mayor), el log de fin de request sale en `ERROR` con la excepción. Así el stack trace queda con `requestId` y el resto del contexto HTTP; el log que escribe el contenedor de esa misma excepción no lo tiene.

### Corregido

- El patrón `EMAIL` tardaba un tiempo cuadrático en textos largos sin `@`: un log de 100 KB tomaba más de un minuto y, con logging asíncrono, frenaba la escritura de los demás logs. Ahora recorre el texto una sola vez.
- Spring Boot: los objetos de `@LogField` ignoraban las estrategias de `masking.strategies`; un campo con `MaskType.CUSTOM` salía enmascarado completo.
- Documentación: en el `application.properties` de Quarkus, una regex de `masking.patterns` necesita `\\\\` por cada `\`. Y `scan="true"` solo sirve con un `logback.xml` en disco.

## [1.0.0] - 2026-11-11

Reescritura completa de la librería. No es compatible con la v1: ver "Migración desde la v1" más abajo.

### Agregado

**Formatos**
- JSON en una línea por evento, con el esquema Pragma: `timestamp`, `level`, `logger`, `thread`, `message`, `app`, `version`, `host`, `pid`, `error`, `stacktrace`.
- JSON indentado (`json.pretty`) para leer en local.
- XML, CSV (columnas, separador y encabezado configurables) y formatos propios implementando `LogEventFormatter`.
- Texto plano legible (`format: plain`) con el mismo masking y colores ANSI por nivel (nombre o hex), solo en consola.
- Patrón y zona horaria del timestamp configurables.

**Campos**
- Campos fijos, ocultar y renombrar cualquier campo (estándar, fijo, MDC o key-value).
- Orden de campos (`field-order`), campos anidados con nombres `$.a.b`, campos obligatorios con valor por defecto y alerta (`required-fields`), y objeto `extra` para MDC y key-values.
- Truncado de mensajes y textos largos (`max-field-length`).
- Ningún log sale sin mensaje: se usa la excepción o `(empty message)`.
- Resumen `error` (tipo, mensaje, causa raíz) y stack trace acotado en profundidad y largo.

**Masking**
- `@MaskedLogField` en campos de cualquier tipo, también heredados, en records y a nivel de clase; tipos FULL, PREFIX, SUFFIX, MIDDLE, HIDDEN y CUSTOM.
- Masking por nombre de campo a cualquier profundidad (password, token, cardNumber…), también en mapas, MDC, key-values y headers.
- Masking por patrón en el texto: tarjetas (con Luhn), emails, JWT y regex propias.
- Nunca usa `toString()` ni modifica el objeto logueado; tolera referencias circulares.

**Contexto**
- `requestId` (del header o generado, validado contra inyección), método, path, status y duración del request en Spring MVC, WebFlux y Quarkus.
- Headers por lista o `*` con lista negra, y query params.
- Captura opcional de bodies de request y response, enmascarados.
- Log de inicio y de fin de cada request.
- `traceId`/`spanId` de Micrometer Tracing.
- Contexto propio en el Reactor Context (`context-keys`).
- Enrichers (`LogEnricher`) y `@LogEnrichment` / `@LogField`.

**Alertas y configuración**
- Alerta por tamaño de log, alerta de fallo de masking y de campos obligatorios faltantes, con listeners propios.
- Truncado del log completo al superar `max-log-size` (`alerts.truncate`).
- Configuración en `application.yml` (Spring, con autocompletado) y `application.properties` (Quarkus); una configuración inválida detiene el arranque.
- Cambios en caliente en Spring (`PragmaLogReloader`) y con `scan` de Logback.
- Advertencias al arrancar sobre configuraciones inconsistentes.
- Logging asíncrono sin pérdida de eventos (`PragmaAsyncAppender`, `pragma.log.async.enabled`).

**Integraciones**
- Java puro con Logback, Spring Boot 3.5 y 4.x (MVC y WebFlux), Quarkus 3.33+ (también en modo nativo).

**Calidad y seguridad**
- Pipeline con lint (Error Prone), tests en Java 17 y 21, cobertura mínima, código muerto (PMD), SonarCloud con quality gate, SAST (SpotBugs + FindSecBugs), dependencias (OSV-Scanner), secretos (gitleaks), tests nativos y publicación con aprobación.
- Escaneo semanal de vulnerabilidades con Dependabot e issue automático.

### Migración desde la v1

- `PragmaLogger` se eliminó: usa SLF4J (`LoggerFactory.getLogger(...)`), que la librería enmascara igual.
- El archivo propio `pragma-log-config.yml` se eliminó: la configuración va en `application.yml`, `application.properties` o `logback.xml`.
- `@DeepMasking` ya no hace falta: el masking es recursivo siempre.
- El objeto `extra` ya no es automático: actívalo con `extra-field: extra`.
- `logback.xml` ya no viene dentro del jar: en Spring Boot y Quarkus no hace falta; en Java puro usa `PragmaStructuredEncoder`.
