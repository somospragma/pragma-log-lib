# pragma-log-lib — Requerimientos v1 (relanzamiento)

> Borrador. Destilado de `SPEC.md`, `TASK_LIST.md`, `MANUAL_USO_LIBRERIA.md`, los planes de enrichers y el código de las dos copias.
> Los IDs (`FMT-01`, `MSK-03`, …) apuntan al catálogo completo en [01-catalogo-funcionalidades.md](01-catalogo-funcionalidades.md).
> Este documento reemplaza a `SPEC.md` como fuente de verdad: lo que no esté aquí no entra en v1.

## 1. Propósito

Que cualquier microservicio Java de Pragma produzca logs **estructurados, seguros y homogéneos** agregando una dependencia y, como mucho, un archivo de configuración — sin cambiar la forma de loguear (`log.info(...)` con SLF4J).

### Propuesta de valor (lo que NO dan Logback/Spring Boot por sí solos)

1. **Esquema de campos Pragma** estándar y estable entre servicios.
2. **Enmascaramiento de datos sensibles** garantizado (nunca se imprime un objeto sin pasar por masking).
3. **Enriquecimiento automático** con contexto HTTP y de trazas (traceId/spanId).
4. **Alertas** de logs anómalos (tamaño) con hooks.
5. **Integración en < 5 minutos.**

Lo que ya resuelve el ecosistema (serializar JSON, appenders, async, rotación) **se reutiliza, no se reimplementa**.

## 2. Usuarios

| Usuario | Necesidad |
|---|---|
| Desarrollador de microservicio | Agregar dependencia y seguir usando `log.info`. Marcar campos sensibles. |
| Equipo de plataforma / observabilidad | Logs JSON con campos predecibles para ELK / Datadog / OTel. |
| Contribuidor de la librería | Agregar formatos, estrategias de masking o integraciones sin tocar el core. |

## 3. Alcance v1

### 3.1 Dentro (MUST)

| ID | Requerimiento | Criterio de aceptación |
|---|---|---|
| FMT-02 | Salida **JSON compacta** (una línea por evento) | Cada línea es JSON válido parseable por `jq`. |
| FMT-01 | Salida **CONSOLE** legible para desarrollo | Formato `timestamp level [thread] logger : message`, colores opcionales. |
| FMT-07 | Selección de formato solo por configuración | Cambiar `format` no requiere tocar el XML de Logback. |
| FLD-01 | Campos estándar: `timestamp`, `level`, `logger`, `thread`, `message`, `app`, `stacktrace` (si hay error) | Presentes siempre (excepto `stacktrace`). |
| FLD-09 | Campos de **MDC** incluidos automáticamente | `MDC.put("x","1")` → aparece `x` en el JSON. |
| FLD-10 | **Argumentos estructurados** (`kv("userId", id)` u objetos en `{}`) se vuelven campos | El objeto se serializa como objeto JSON, no como `toString()`. |
| FLD-02/03 | Ocultar y renombrar campos por config | `hide: [thread]` y `rename: {level: severity}` se reflejan. |
| ERR-01 | Stacktrace configurable (incluir / no incluir / profundidad máxima) | Con `false` solo aparece clase + mensaje de la excepción. |
| MSK-01/03 | `@MaskedLogField` con tipos `FULL`, `PREFIX`, `SUFFIX`, `MIDDLE`, `HIDDEN` | Tests por tipo; incluye campos no-String y heredados. |
| MSK-05 | Masking **recursivo** (objetos anidados, colecciones, mapas, records) | Un campo anotado dentro de una lista de objetos sale enmascarado. |
| MSK-06 | Tolerante a referencias circulares | No hay `StackOverflowError`. |
| MSK-07 | Ningún argumento se imprime vía `toString()` sin pasar por masking (incluido el `{}` del mensaje) | Test con `toString()` que expone el secreto: el secreto no aparece. |
| MSK-08 | El masking no modifica el objeto de negocio | Después de loguear, el objeto conserva su valor original. |
| MSK-09 | Masking **por nombre de campo** en config (para clases de terceros sin anotación) | `masking.fields: [password, cardNumber]` enmascara aunque no haya anotación. |
| MSK-02 | Config global: habilitado, carácter, tipo por defecto | — |
| CTX-01 | Correlación: `traceId`/`spanId` desde OpenTelemetry / Micrometer Tracing si están en el classpath | Aparecen en el JSON sin configuración. |
| INT-01 | **Java puro** (SLF4J + Logback) | Proyecto de ejemplo funcionando con solo la dependencia + `logback.xml` de 3 líneas. |
| INT-02 | **Spring Boot 3 starter** (auto-config, `application.yml`, sin XML obligatorio) | Proyecto de ejemplo: agregar dependencia → logs JSON. |
| INT-06/07 | La librería no empaqueta `logback.xml` ni fuerza binding SLF4J en el core | El consumidor conserva su configuración de Logback. |
| ALR-05 | Fallback ante errores internos: nunca rompe la app ni pierde el log | Test con un objeto que lanza excepción al serializar: el log sale igual. |
| CFG-01 | Defaults seguros: funciona sin ningún archivo de configuración | — |
| CFG-05 | Validación de configuración al arrancar con mensajes claros | Config inválida → warning explícito, no excepción silenciosa. |
| PERF-01 | Overhead medido con JMH contra Logback/logstash puro | Reporte publicado; objetivo a definir (p.ej. < 2× del encoder base). |
| INT-03 | **Spring WebFlux** (mismo starter, contexto reactivo → MDC) | App de ejemplo: el `requestId` aparece en logs emitidos desde otro hilo del pipeline. |
| CTX-08 | **Variables propias del Reactor Context** en los logs (`pragma.log.context-keys`) | `contextWrite(Context.of("userId", id))` → aparece `userId` en todos los logs de ese flujo, en cualquier hilo, sin código extra. |
| INT-04 | **Quarkus** (extensión sobre JBoss LogManager, incluido modo nativo) | App de ejemplo JVM y nativa con JSON + masking. |
| PUB-01 | Publicación en **Maven Central** (ver ADR-04/05) | `implementation '<groupId>:pragma-log-...'` funciona en un proyecto limpio con solo `mavenCentral()`. |

### 3.2 Dentro si alcanza (SHOULD)

| ID | Requerimiento |
|---|---|
| ENR-01 | Enriquecimiento HTTP básico en Spring MVC: método, path, status, duración, `requestId` (header o generado). |
| ENR-03 | Captura de headers con **whitelist** (nunca todos por defecto). |
| ALR-01/02 | Alerta por tamaño de log (`maxLogSize`) + hook `LogAlertListener`. |
| FLD-08 | Truncado de campos de texto largos (`maxFieldLength`). |
| MSK-10 | Masking por regex sobre el mensaje (tarjetas, emails, JWT). |
| ALR-03 | Hook `onMaskingError` funcionando. |
| MSK-04 | Estrategias de masking custom registrables. |
| FMT-05 | Colores por nivel en CONSOLE. |
| FLD-05 | Campos fijos por config (`env`, `team`, `version`). |

### 3.3 Fuera de v1 (LATER)

Captura de bodies de request/response (ENR-04), `additional-fields` con `path` JSONPath (FLD-05b), orden de campos configurable (FLD-04), `requiredFields` (FLD-07), modos de serialización `JAVA_TOSTRING`/`NONE` (SER-02), JSON pretty (FMT-03), formatos custom XML/CSV (FMT-04), hot-reload (CFG-06), Docusaurus (DOC-03). Micronaut y Vert.x (INT-05) van en la siguiente versión (decidido 2026-10-31); Quarkus sí entra en v1.

## 4. Requisitos no funcionales

| ID | Requisito |
|---|---|
| NFR-01 | Java **17** como mínimo; CI también en 21 (ADR-02). |
| NFR-02 | Cero dependencias de framework en el core. |
| NFR-03 | Sin reflexión en el camino caliente: metadata de clases cacheada (o generada) una sola vez por tipo. |
| NFR-04 | Thread-safe; sin estado mutable compartido salvo caches concurrentes. |
| NFR-05 | Un error dentro de la librería **nunca** rompe la aplicación ni pierde el log: se degrada a la salida estándar de Logback y notifica por hook. |
| NFR-06 | Cobertura de tests ≥ 80 % en core; un test de integración por módulo con app real. |
| NFR-07 | Versiones de dependencias centralizadas (BOM / version catalog), sin mezclas (p. ej. Jackson 2.17 + 2.15). |
| NFR-08 | Código en inglés; documentación de usuario en español. |

## 5. Decisiones técnicas

Ver [03-decisiones-arquitectura.md](03-decisiones-arquitectura.md). Aceptadas: logstash-logback-encoder (ADR-01), Java 17 (ADR-02), las cuatro integraciones en serie (ADR-03), Maven Central (ADR-04). Pendientes de tu confirmación: groupId (ADR-05) y licencia.

## 6. Plan de entregas

1. **E0 — Consolidación:** ramas `legacy/libs` y `legacy/nexus` con todo el trabajo sin commitear; rama `v2` limpia (ADR-14).
2. **E1 — Esqueleto (hecho 2026-10-29):** Gradle multi-módulo (`core`, `logback`, `bom`, `examples/plain-java`), version catalog, convention plugins, CI en Java 17/21, configuración de publicación a Central (sin publicar). Encoder JSON mínimo funcionando de punta a punta. Los módulos `spring-boot-starter` y `quarkus` se crean en su entrega, no como cascarones vacíos.
3. **E2 — Java puro → `0.1.0`:** JSON + esquema + MDC + key-values + masking completo (MSK-01..09) + fallback (ALR-05), con app de ejemplo.
   - *Hecho (2026-10-29):* masking MSK-01..09 sobre Jackson 3 (anotación, nombres, mapas, records, herencia, ciclos, profundidad, estrategias custom), masking de `{}`/MDC/key-values en el encoder, fallback ALR-05, config inválida → defaults seguros (CFG-05), tests de regresión de los 6 bugs.
   - *Hecho (2026-10-29):* ocultar/renombrar cualquier campo (FLD-02/03), campos fijos (FLD-05), stacktrace configurable y acotado (ERR-01/03), resumen `error` con causa raíz (ERR-02/04), JMH inicial ([04-rendimiento.md](04-rendimiento.md)).
   - *Funcionalmente completa.* Para publicar `0.1.0` faltan el groupId (ADR-05), la licencia y el flujo de release a Central.
4. **E3 — Spring MVC → `0.2.0`:** starter, `@ConfigurationProperties`, filtro HTTP, trazas (CTX-01).
   - *Hecho (2026-10-29):* starter sin XML, activado como formato de *structured logging* de Spring Boot; `pragma.log.*` con metadata para el IDE; `app` desde `spring.application.name`; filtro HTTP con `requestId` (seguro contra inyección), método, path y headers en lista permitida, restaurando el MDC previo; `format: plain` para local; config inválida detiene el arranque; compatibilidad Boot 3.5 y 4.x probada en cada build (spike ADR-12); app de ejemplo.
   - *Pendiente / no verificado:* `traceId`/`spanId` con Micrometer Tracing no tiene test propio (se apoya en el MDC que escribe Micrometer); status y duración del request (ENR-01) no se agregan todavía.
5. **E4 — WebFlux → `0.3.0`:** propagación de contexto reactivo (spike ADR-12 al inicio).
   - *Hecho (2026-10-29):* `WebFilter` que pone el contexto del request en el Reactor Context; `ThreadLocalAccessor` (context-propagation) que lo copia al MDC en cada hilo, tocando solo sus claves; propagación automática activada por el starter. Tests contra Boot 4.1 y 3.5: el contexto sobrevive a cambios de hilo, no se mezcla entre 30 requests concurrentes y no queda pegado después del request. Se verificó que los tests fallan sin el accessor. App de ejemplo.
   - *Hecho (2026-10-31):* CTX-08, variables propias del Reactor Context declaradas en `pragma.log.context-keys` salen como campos en cualquier hilo, también fuera de requests HTTP; claves reservadas detienen el arranque. Tests en Boot 4.1 y 3.5 (20 requests concurrentes, sin fugas); fallan sin el registro de los accessors.
   - *Nota:* la propagación automática tiene un costo por operador; no está medido todavía con JMH.
6. **E5 — Quarkus → `0.4.0`:** spike ADR-11 + extensión.
7. **E6 — SHOULDs + JMH + docs → `1.0.0`** en Maven Central.

## 7. Qué se rescata del código actual

Ninguna de las dos copias funciona sola: **A** emite logs pero tiene un masking con fugas; **B** tiene mejor masking y enrichers, pero no emite logs y `logging-spring` no compila. Sus núcleos de masking y JSON son incompatibles, así que fusionarlas cuesta más que portar piezas a una base limpia.

| Se rescata (adaptando) | De | Para |
|---|---|---|
| Estrategias `Prefix/Suffix/Middle/Full/Hidden` + `StrategyRegistry` | ambas | MSK-03/04 (corregir MIDDLE que acorta y usar TCCL) |
| `@MaskedLogField` | ambas | MSK-01 |
| Recorrido con `IdentityHashMap` + cache de metadata (`TypeInspector`) | B | MSK-05/06 (agregar colecciones, mapas, records; excluir clases del JDK) |
| `ExceptionFormatter` (resumen con cadena de causas) | ambas | ERR-01/02 |
| `ConsoleFormatter`, `AnsiColor`, `LogLevelColor` | ambas | FMT-01/05 |
| `LogContextInterceptor` (requestId/traceId → MDC) | A | CTX-02/04 (sin `MDC.clear()` global) |
| `RequestSnapshot` / `ResponseSnapshot` | B | ENR-01 |
| Tests de masking y formatters | ambas | Casos de prueba de referencia |

| Se descarta | Por qué |
|---|---|
| `LoggingConfigLoader` + `ObjectMergeHelper` | Errores silenciosos y booleanos mal fusionados; lo reemplaza la config del framework (ADR-2). |
| `PragmaLogger` | Duplica SLF4J, formatea dos veces y propaga excepciones. |
| 3 encoders idénticos | Se reemplazan por uno solo. |
| Aspectos `@LogEnrichment` y holders reactivos | No compilan o nunca enriquecen; se rediseña con un filtro. |
| `logback.xml` dentro de los jars | Pisa la configuración del consumidor. |

### Bugs de seguridad a no repetir (tests obligatorios en v1)

1. El `{}` del mensaje usa `toString()` → secretos en claro (MSK-07).
2. El masking modifica el objeto de negocio original (MSK-08).
3. `@MaskedLogField` en un campo no-String filtra el valor (MSK-01).
4. Campos heredados de superclases no se enmascaran (MSK-01).
5. Con masking activo, las excepciones pierden el stacktrace (ERR-01).
6. Un error en el YAML de config cae en silencio a defaults — incluido `masking.enabled` (CFG-05).
