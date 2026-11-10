# pragma-log-lib — Catálogo completo de funcionalidades y configuraciones

> Inventario de **todo** lo que alguna vez se pidió o se construyó, para decidir qué entra en v1.
> Fuentes: `SPEC.md`, `TASK_LIST.md`, `MANUAL_USO_LIBRERIA.md`, `FORMATTER_ARCHITECTURE.md`, planes de enrichers (`plan-*.md`, `ENRICHERS.md`, `USAGE_SPRING_MVC.md`) y el código de las dos copias:
> - **A** = `repos/pragma/libs/pragma-log-lib` (integraciones que sí emiten logs: encoders, interceptor, PragmaLogger, handler Quarkus)
> - **B** = `repos/pragma/nexus-pragma/pragma-log-lib` (deep masking, enrichers, `@LogEnrichment`; varias partes no compilan)
>
> Ver priorización en [02-requerimientos-v1.md](02-requerimientos-v1.md).

**Leyenda**
- **Origen:** `S` = pedido en spec/docs · `C` = existe solo en código · `N` = propuesta nueva (hueco detectado)
- **Estado actual:** ✅ completo · 🟡 parcial · 🔴 roto / no compila · ⚪ stub o solo declarado · — no existe
- **v1:** `MUST` · `SHOULD` · `LATER` · `DROP` (se descarta)

---

## Parte 1 — Funcionalidades

### 1.1 Formatos de salida (FMT)

| ID | Funcionalidad | Origen | Estado actual | v1 | Notas |
|---|---|---|---|---|---|
| FMT-01 | Formato CONSOLE legible (`timestamp level [thread] logger : msg`) | S | ✅ A/B | MUST | Hoy sale `ts [LEVEL] msg k=v`, no el layout de la spec. **Hecho en v2 (2026-11-02):** `format: plain` con el layout de la spec y el mismo masking que el JSON, en Logback (`PragmaTextEncoder`), Spring y Quarkus. |
| FMT-02 | JSON compacto, una línea por evento | S | 🟡 A/B | MUST | A convierte todos los valores a String (pierde tipos); B no formatea timestamp. |
| FMT-03 | JSON pretty (`prettyPrint`) | S | ✅ | MUST | Rompe los log shippers (multilínea); solo útil en local. **v1 (2026-11-09):** entra por decisión de producto. **Hecho en v2 (2026-11-10):** `json.pretty`. |
| FMT-04 | Formatos custom (XML, CSV…) vía `LogFormatter` | S | 🟡 | MUST | Clase abstracta existe; no hay registro por config. **v1 (2026-11-09):** entra por decisión de producto. **Hecho en v2 (2026-11-10):** `format: xml|csv|custom`; `LogEventFormatter` para formatos propios. |
| FMT-05 | Colores ANSI por nivel (nombre o hex) | S | 🟡 | SHOULD | Solo nombres `AnsiColor`, hex no soportado; colorea la línea completa. **Hecho en v2 (2026-11-02):** color solo en el nivel (configurable por nombre o hex), timestamp tenue y logger en cian; `auto` respeta terminal y `NO_COLOR`; los archivos nunca llevan color. |
| FMT-06 | Formato de timestamp configurable (default ISO-8601 en JSON) | S | 🟡 | MUST | Zona horaria no configurable; default actual no es ISO. **Hecho en v2 (2026-11-02):** `timestamp.pattern` (patrón o constante `[ISO_INSTANT]`) y `timestamp.zone` en JSON y texto; default ISO-8601 con offset en JSON. |
| FMT-07 | Seleccionar formato solo por config, sin tocar XML | S | 🟡 A | MUST | En A funciona leyendo `formatter`. |
| FMT-08 | Formatos estándar de mercado: ECS (Elastic), Logstash, GELF | N | — | LATER | Spring Boot 3.4+ ya los trae; evaluar si el esquema Pragma se alinea a ECS. |
| FMT-09 | Formato distinto por perfil/entorno (CONSOLE en local, JSON en prod) | N | — | SHOULD | Hoy se haría con dos archivos; en Spring sale gratis con perfiles. **Hecho en v2 (2026-11-02):** perfiles de Spring y `%dev.` de Quarkus, documentado. |

### 1.2 Campos y esquema (FLD)

| ID | Funcionalidad | Origen | Estado actual | v1 | Notas |
|---|---|---|---|---|---|
| FLD-01 | Campos estándar: timestamp, level, thread, logger/className, appName, message, stacktrace | S | 🟡 | MUST | `appName` y `className` nunca se inyectan (los encoders usan `logger`). |
| FLD-02 | Ocultar campos (`hideFields`) | S | ✅ A / 🟡 B | MUST | B no deja ocultar timestamp/level/message. |
| FLD-03 | Renombrar campos (`renameFields`) | S | ✅ | MUST | Clave para mapear a esquemas de ELK/Datadog. |
| FLD-04 | Ordenar campos (`fieldsOrder`) | S | ✅ | MUST | Poco valor en JSON; útil en CONSOLE. **v1 (2026-11-09):** entra por decisión de producto. **Hecho en v2 (2026-11-10):** `field-order`, en todos los formatos. |
| FLD-05 | Campos adicionales (`additionalFields`) con source `fixed` | S | 🟡 | SHOULD | Valores fijos: env, team, version. |
| FLD-05b | `additionalFields` con `path` JSONPath (`$.request.error`) para anidar | S | 🟡 B | MUST | Solo B en JsonFormatter. **v1 (2026-11-09):** entra por decisión de producto. **Hecho en v2 (2026-11-10):** un nombre `$.a.b` en `rename-fields` o `extra-field` anida el campo. |
| FLD-06 | Objeto `extra` para campos no mapeados | S | — | MUST | Mejor campos en raíz o bajo un prefijo fijo. **v1 (2026-11-09):** entra por decisión de producto. **Hecho en v2 (2026-11-10):** `extra-field` agrupa MDC y key-values, salvo los nombrados en la configuración. |
| FLD-07 | Campos obligatorios (`requiredFields`) con validación | S | ✅ | MUST | Hoy lanza excepción: en PragmaLogger llega a la app. Nunca debe romper. **v1 (2026-11-09):** entra por decisión de producto. **Hecho en v2 (2026-11-10):** `required-fields`; si falta, sale con `required-placeholder` y alerta `onMissingFields`. Nunca rompe. |
| FLD-08 | Truncar textos largos (`maxSizeText`) | S | ✅ | SHOULD | A convierte objetos a String al truncar; truncar solo Strings. **Hecho en v2 (2026-11-02):** `max-field-length` trunca el mensaje y los valores de texto (también dentro de objetos) después de enmascarar; números y stack trace no. |
| FLD-09 | Incluir MDC automáticamente | S | ✅ A | MUST | En los encoders de A. |
| FLD-10 | Argumentos estructurados (`log.info("msg", Map)`, `kv(...)`, objetos en `{}`) como campos | S | 🟡 A | MUST | A los agrega como `arg0..N`; el mensaje con `{}` usa `toString()` sin masking. |
| FLD-11 | Nunca mensaje vacío | S | — | MUST | — **v1 (2026-11-09):** entra por decisión de producto. |
| FLD-12 | Versión de la app, host, pid, entorno | N | — | SHOULD | Campos típicos de observabilidad. Hecho en v2 (2026-10-31): `version`, `host` (default), `pid` (opt-in). |

### 1.3 Excepciones (ERR)

| ID | Funcionalidad | Origen | Estado actual | v1 | Notas |
|---|---|---|---|---|---|
| ERR-01 | Incluir o no el stacktrace (`includeStacktrace`) | S | 🟡 | MUST | En A, con masking activo, la excepción pasa por el masker y se pierde la traza. |
| ERR-02 | Resumen con cadena de causas cuando no hay stacktrace | C | ✅ | MUST | Rescatable (`ExceptionFormatter`). |
| ERR-03 | Profundidad máxima / recorte de frames del stacktrace | N | — | SHOULD | Controla el tamaño de los logs de error. |
| ERR-04 | Campos separados `error.type`, `error.message`, `error.stack` | N | — | SHOULD | Facilita búsquedas en ELK/Datadog. |

### 1.4 Enmascaramiento (MSK)

| ID | Funcionalidad | Origen | Estado actual | v1 | Notas |
|---|---|---|---|---|---|
| MSK-01 | `@MaskedLogField(type, length, strategy)` en campos | S | 🟡 | MUST | A: solo String, en otros tipos fuga vía `toString()`. B: ignora no-String. Ninguna lee campos heredados. |
| MSK-01b | `@MaskedLogField` a nivel de clase | S | 🟡 A | MUST | A enmascara el `toString()` entero; B lo ignora. **v1 (2026-11-09):** entra por decisión de producto. **Hecho en v2 (2026-11-10):** en una clase enmascara cada campo; la anotación del campo manda. |
| MSK-02 | Config global: `enabled`, `character`, `defaultType`, `defaultLength` | S | 🟡 | MUST | `defaultType` es config muerta; null en `character`/`defaultLength` causa NPE. |
| MSK-03 | Estrategias built-in: PREFIX, SUFFIX, MIDDLE, FULL, HIDDEN | S | ✅ | MUST | MIDDLE acorta la cadena; HIDDEN difiere entre A (`""`) y B (`null`). |
| MSK-04 | Estrategias custom registradas por nombre (`masking.strategies`) | S | ✅ | SHOULD | En A, las built-in pisan a las del usuario; usar TCCL en vez de `Class.forName`. |
| MSK-05 | Masking recursivo en objetos anidados (`@DeepMasking`, `deepScanPackages`) | S | 🟡 B | MUST | No recorre colecciones/mapas/arrays; falla con records y clases del JDK (Java 21). |
| MSK-06 | Tolerancia a referencias circulares | C | ✅ B | MUST | `IdentityHashMap` de visitados en B. |
| MSK-07 | **Nunca** imprimir un objeto sin pasar por masking (incluido el `{}` del mensaje) | S | 🔴 | MUST | Hoy el mensaje formateado filtra secretos vía `toString()`. |
| MSK-08 | No mutar el objeto de negocio (enmascarar una copia/representación) | N | 🔴 | MUST | Hoy ambas copias modifican el objeto original: la app ve el password enmascarado. |
| MSK-09 | Masking por **nombre de campo** en config (clases de terceros sin anotación) | N | — | MUST | `password`, `cardNumber`, `token`… en cualquier nivel. |
| MSK-10 | Masking por **patrón/regex** sobre el mensaje (tarjetas, emails, JWT) | N | — | SHOULD | Red de seguridad para lo que se escapa de las anotaciones. **Hecho en v2 (2026-11-02):** `masking.patterns` con `CARD` (Luhn), `EMAIL`, `JWT` o regex (con grupo), en mensaje, MDC, key-values, stack trace y resumen `error`. Apagado por defecto. |
| MSK-11 | Masking de headers sensibles (`Authorization`, `Cookie`) | S | 🟡 B | MUST* | *Si entra ENR-03. Hecho en v2 (2026-10-31). |
| MSK-12 | Tipos de masking extensibles (REGEX, EMAIL…) | S | 🟡 | LATER | Hoy vía `CUSTOM` + nombre. |

### 1.5 Serialización de objetos (SER)

| ID | Funcionalidad | Origen | Estado actual | v1 | Notas |
|---|---|---|---|---|---|
| SER-01 | Objetos serializados como JSON (Jackson) | S | 🟡 | MUST | Solo se aplica en algunos caminos (ver config). |
| SER-02 | Modos `JAVA_TOSTRING` / `NONE` (`objectSerialization`) | C | 🟡 | LATER | — |
| SER-03 | Soporte de records, `Optional`, `java.time` | N | 🔴 | MUST | Deep masking falla con records. |

### 1.6 Enriquecimiento y contexto (ENR / CTX)

| ID | Funcionalidad | Origen | Estado actual | v1 | Notas |
|---|---|---|---|---|---|
| CTX-01 | traceId / spanId desde OpenTelemetry / Micrometer Tracing | S | — | MUST | Hoy solo vía header `X-Trace-Id`; ni rastro de OTel. Hecho en v2 (2026-10-31): verificado con Micrometer Tracing + Brave. |
| CTX-02 | requestId: desde header o generado (UUID) | C | ✅ A | SHOULD | `LogContextInterceptor`. |
| CTX-03 | Datos custom de negocio en el contexto (`addCustomData`) | S | 🟡 B | SHOULD | Equivale a MDC; evaluar si basta con MDC + helper. |
| CTX-04 | Propagación en Spring MVC (ThreadLocal / MDC) | S | ✅ A / 🟡 B | MUST* | *Con el starter. A hace `MDC.clear()` global: borra MDC ajeno. |
| CTX-05 | Propagación en WebFlux (Reactor Context → MDC) | S | 🔴 | MUST | Sin puente Context→MDC; B hace `block()`/`subscribe()` sueltos. |
| CTX-06 | Propagación en Quarkus (Vert.x context) | S | ⚪ | MUST | — Hecho en v2 (2026-10-31): MDC en el contexto duplicado de Vert.x. |
| CTX-07 | Limpieza obligatoria del contexto al final del request (sin memory leaks) | S | 🟡 | MUST | — |
| CTX-08 | Variables propias del contexto en los logs: claves declaradas en config (`pragma.log.context-keys`) que viajan en el Reactor Context y salen como campos | N | — | MUST | Pedido en el relanzamiento (2026-10-31). En MVC basta con MDC. |
| ENR-01 | Contexto HTTP: método, path, status, duración | S | 🟡 B | SHOULD | B captura antes de ejecutar el método (status inválido). Hecho en v2 (2026-10-31): log de fin de request con status y duración. |
| ENR-02 | `additionalFields` desde `header` / `query` | S | 🟡 A | MUST | A vía MDC en el interceptor. **v1 (2026-11-09):** entra por decisión de producto. **Hecho en v2 (2026-11-10):** `http.query-params` (lista o `*`), leídos solo de la URL. |
| ENR-03 | Captura de headers con whitelist/blacklist | S | 🟡 B | MUST | Por defecto: whitelist vacía. **v1 (2026-11-09):** entra por decisión de producto. **Hecho en v2 (2026-11-10):** `http.headers: "*"` con `http.exclude-headers` (credenciales por defecto). |
| ENR-04 | Captura de request/response body (opt-in) | S | ⚪ B | MUST | Coste y riesgo de datos sensibles altos. **v1 (2026-11-09):** entra por decisión de producto. **Hecho en v2 (2026-11-10):** `http.body` (apagado por defecto), JSON y formularios enmascarados por nombre, límite `max-size`; en Quarkus para Quarkus REST. |
| ENR-05 | `@LogEnrichment` por clase/método vía AOP | S | 🔴 B | MUST | (ADR-10) Un filtro global configurable es más simple que AOP por anotación. **v1 (2026-11-09):** entra por decisión de producto. **Hecho en v2 (2026-11-10):** `@LogEnrichment` + `@LogField` en beans de Spring (spring-aop, sin AspectJ) y CDI; sigue a `Mono`/`Flux`/`Uni`. |
| ENR-06 | Usuario autenticado desde Spring Security | S | — | DROP | — **Fuera (2026-11-09):** decisión de producto. |
| ENR-07 | Enrichers custom (`LogEnricher`) registrables | S | ⚪ | MUST | Interfaz existe, sin conectar al pipeline. **v1 (2026-11-09):** entra por decisión de producto. **Hecho en v2 (2026-11-10):** `LogEnricher` por config, código o bean (Spring y CDI); campos enmascarados. |
| ENR-08 | Log de acceso por request (entrada/salida) | N | — | MUST | Útil y frecuente; evaluar. **v1 (2026-11-09):** entra por decisión de producto. **Hecho en v2 (2026-11-10):** `http.request-log.entry`. |

### 1.7 Alertas y hooks (ALR)

| ID | Funcionalidad | Origen | Estado actual | v1 | Notas |
|---|---|---|---|---|---|
| ALR-01 | Umbral de tamaño de log (`maxLogSize`) con alerta en consola | S | 🟡 | SHOULD | Solo funciona si hay `hooks` configurados; mide chars, no bytes. **Hecho en v2 (2026-11-02):** `alerts.max-log-size` en bytes UTF-8; aviso en stderr limitado a uno por minuto por logger, sin listeners configurados. |
| ALR-02 | Hooks `LogAlertListener` registrables por config o código | S | 🟡 | SHOULD | Por código no funciona si `hooks` está vacío. **Hecho en v2 (2026-11-02):** por config (`alerts.listeners`), por código (`LogAlerts.register`) y como bean de Spring; un listener que falla o que loguea no rompe ni genera alertas en bucle. |
| ALR-03 | Hook `onMaskingError` | S | ⚪ | SHOULD | Nunca se dispara. **Hecho en v2 (2026-11-02):** se dispara en cada fallback de masking o encoding (cubre también ALR-05). |
| ALR-04 | Truncar el log si supera el umbral (opcional) | S | — | MUST | — **v1 (2026-11-09):** entra por decisión de producto. **Hecho en v2 (2026-11-10):** `alerts.truncate` recorta los campos más grandes hasta caber, con `"truncated":true`. |
| ALR-05 | Hook de error interno de la librería (fallback) | N | — | MUST | Ligado a NFR-05: la librería nunca rompe ni pierde logs. |

### 1.8 Configuración (CFG)

| ID | Funcionalidad | Origen | Estado actual | v1 | Notas |
|---|---|---|---|---|---|
| CFG-01 | Defaults seguros sin archivo de config | S | ✅ | MUST | — |
| CFG-02 | Archivo propio `pragma-log-config.yml` / `.properties` en classpath | S | 🟡 | DROP | Properties en kebab-case no funciona. **Descartado (2026-11-09):** en Java puro la configuración va en `logback.xml`; en Spring y Quarkus, en su archivo de configuración. |
| CFG-03 | Merge profundo base + usuario | S | 🟡 | MUST | Booleanos omitidos quedan en `false`; listas reemplazan. |
| CFG-04 | Config desde `application.yml` en Spring (`pragma.log.*`) | S | — | MUST | La spec lo pide; el código no lo hace. |
| CFG-05 | Validación con errores claros | S | 🔴 | MUST | Hoy un error de YAML cae **en silencio** a la config base. |
| CFG-06 | Hot-reload de configuración | S | — | MUST | — **v1 (2026-11-09):** entra por decisión de producto. **Hecho en v2 (2026-11-10):** Spring con `EnvironmentChangeEvent` o `PragmaLogReloader.reload()`; Java puro con `scan` de Logback. Quarkus no (decisión de producto). |
| CFG-07 | Config en Quarkus (`application.properties`) | S | — | MUST | — Hecho en v2 (2026-10-31): `pragma.log.*` en `application.properties`. |
| CFG-08 | Override por variables de entorno / system properties | N | 🟡 | SHOULD | Solo existe `pragma.log.forceColors`. |
| CFG-09 | Advertir inconsistencias (p. ej. `multiThread: true` sin AsyncAppender) | S | — | MUST | — **v1 (2026-11-09):** entra por decisión de producto. **Hecho en v2 (2026-11-10):** `ConfigurationAdvice`: advertencias únicas al arrancar en Spring, Quarkus y Logback. |

### 1.9 Rendimiento (PERF)

| ID | Funcionalidad | Origen | Estado actual | v1 | Notas |
|---|---|---|---|---|---|
| PERF-01 | Benchmarks JMH + pruebas de estrés | S | — | MUST | — |
| PERF-02 | Logging asíncrono (`multiThread`) | S | ⚪ | MUST | Config muerta; delegar en `AsyncAppender` de Logback. **Hecho en v2 (2026-11-10):** `PragmaAsyncAppender` (sin descartar, flush al apagar, enrichers en el hilo que loguea); Spring `pragma.log.async.enabled`; Quarkus con su `quarkus.log.*.async`. Probado con 8 hilos concurrentes. |
| PERF-03 | Evitar reflexión en caliente (cache o annotation processor) | S | 🟡 | MUST | A hace `getDeclaredFields` por evento; B cachea. |
| PERF-04 | Jackson Afterburner/Blackbird | S | — | LATER | Medir antes. |

### 1.10 Integraciones (INT)

| ID | Funcionalidad | Origen | Estado actual | v1 | Notas |
|---|---|---|---|---|---|
| INT-01 | Java puro (SLF4J + Logback encoder) | S | ✅ A | MUST | `PragmaLogbackEncoder`. |
| INT-01b | API propia `PragmaLogger` | C | 🟡 A | DROP | Formatea dos veces y propaga excepciones; SLF4J basta. **Descartado (2026-11-09):** todo pasa por SLF4J, como en cualquier librería; la librería enmascara lo que llega por SLF4J. |
| INT-02 | Spring Boot starter (auto-config, sin XML obligatorio) | S | 🟡 | MUST | A: sin `AutoConfiguration.imports`; B no compila. |
| INT-02b | Spring tradicional (sin Boot) | S | — | DROP | — **Fuera (2026-11-09):** decisión de producto. |
| INT-03 | Spring WebFlux | S | 🔴 | MUST | — |
| INT-04 | Quarkus (SLF4J, JBoss Logging, Mutiny) | S | 🟡 A | MUST | Handler JUL con `String.format`, sin MDC, sin Jandex. Hecho en v2 (2026-10-31): extensión con binding SLF4J propio. Nativo verificado (2026-11-03) con job de CI. |
| INT-05 | Micronaut, Vert.x | S | — | DROP | Decidido (2026-10-31): fuera de v1, van en la siguiente versión. **Fuera (2026-11-09):** decisión de producto. |
| INT-06 | No imponer `logback.xml` dentro del jar | N | 🔴 A | MUST | A empaqueta `logback.xml` y pisa la config del consumidor. |
| INT-07 | Core sin binding SLF4J (`logback-classic` no en core) | N | 🔴 A | MUST | Choca con Quarkus. |

### 1.11 Observabilidad, docs y publicación (OBS / DOC / PUB)

| ID | Funcionalidad | Origen | Estado actual | v1 | Notas |
|---|---|---|---|---|---|
| OBS-01 | Mapeo de campos a OTel / ELK / Datadog documentado | S | — | MUST | — **v1 (2026-11-09):** entra por decisión de producto. |
| OBS-02 | Validación real contra un stack (p. ej. ELK en docker) | S | — | LATER | — |
| DOC-01 | Manual de usuario y guía de contribución | S | 🟡 | MUST | Desactualizados respecto al código. |
| DOC-02 | Ejemplos por framework (proyectos de prueba) | S | 🟡 A | MUST | Los demos actuales no prueban lo que dicen (password en claro). |
| DOC-03 | Sitio Docusaurus / GitHub Pages | S | — | LATER | — |
| PUB-01 | Publicar en repo Maven | S | ⚪ | MUST | `maven-publish` aplicado, sin repo destino. |
| PUB-02 | CI con build, tests y cobertura | N | — | MUST | — Hecho en v2 (2026-11-09): `pipeline.yml` con Quality (Error Prone, Java 17, tests + cobertura mínima, PMD, nativo), Security (OSV-Scanner, gitleaks, SpotBugs + FindSecBugs), Build y publicación con aprobación. |
| PUB-03 | Versionado semántico y changelog | N | — | MUST | — **v1 (2026-11-09):** entra por decisión de producto. |
| PUB-04 | BOM / version catalog de dependencias | N | 🔴 | MUST | Hoy: Spring 3.3.1 vs 6.1.3/3.2.1, Jackson 2.17 vs 2.15, JUnit 5.10 vs 5.8, Mockito `4.+`. |

---

## Parte 2 — Configuraciones

Nombres tal como aparecen **en el código** (camelCase). La spec y el manual usan otros (kebab-case, `char`, raíz `pragma-log-config:`, `hooks` dentro de `formatter-options`) — ver Parte 3.

### 2.1 Generales

| Clave | Tipo | Default código | Default spec | Estado | v1 | Notas |
|---|---|---|---|---|---|---|
| `formatter` | `CONSOLE` \| `JSON` | `CONSOLE` | `JSON` | ✅ A / muerta B | MUST | Renombrar a `format`. |
| `appName` | String | `LOG-APP` | `app` | ⚪ muerta | MUST | Nunca se inyecta. En Spring tomar `spring.application.name`. |
| `multiThread` | boolean | `true` | — | ⚪ muerta | SHOULD | Delegar en `AsyncAppender`. |
| `maxLogSize` | Integer (chars) | `90000` | `2000` | 🟡 | SHOULD | Solo si hay hooks; null → NPE. |
| `hooks` | List&lt;FQCN&gt; | `null` | — | ✅ | SHOULD | — |
| `hideFields` | List&lt;String&gt; | `null` | — | ✅ | MUST | — |
| `renameFields` | Map&lt;String,String&gt; | `null` | — | ✅ | MUST | — |
| `fieldsOrder` | List&lt;String&gt; | `timestamp, level, appName, className, message` | — | ✅ | LATER | Referencia campos que no existen. |
| `additionalFields[].name` | String | — | — | 🟡 | SHOULD | — |
| `additionalFields[].source` | `header`\|`query`\|`fixed`\|`context`\|`body`\|`requestBody`\|`responseBody` | — | `header, query, body, fixed, context` | 🟡 | SHOULD (`fixed`, `header`) | A: header/query/fixed. B: todos pero sin conectar. |
| `additionalFields[].key` | String | — | — | 🟡 | SHOULD | — |
| `additionalFields[].value` | String | — | — | 🟡 | SHOULD | Solo `fixed`. |
| `additionalFields[].path` | JSONPath | — | — | 🟡 B / muerta A | LATER | — |

### 2.2 `formatterOptions`

| Clave | Tipo | Default código | Default spec | Estado | v1 | Notas |
|---|---|---|---|---|---|---|
| `timestampFormatter` | patrón `DateTimeFormatter` | `yyyy/MM/dd HH:mm:ss.SSS` | ISO-8601 en JSON | 🟡 | MUST | Agregar `timeZone` (default UTC). |
| `prettyPrint` | boolean | `false` | `false` | ✅ | LATER | — |
| `objectSerialization` | `JSON`\|`JAVA_TOSTRING`\|`NONE` | `JSON` | — | 🟡 | LATER | — |
| `maxSizeText` | Integer | `9000` | — | ✅ | SHOULD | Renombrar a `maxFieldLength`. |
| `includeStacktrace` | boolean | `true` | `true` | 🟡 | MUST | — |
| `requiredFields` | List&lt;String&gt; | `message, timestamp, level` | — | ✅ | LATER | No debe lanzar excepción hacia la app. |
| `colorConfig.colorize` | boolean | `false` | — | ✅ | SHOULD | Requiere TTY o `-Dpragma.log.forceColors=true`. |
| `colorConfig.colors` | Map&lt;LogLevel, AnsiColor&gt; | ERROR=BRIGHT_RED, WARNING=YELLOW, INFO=BLUE, DEBUG=WHITE, TRACE=BRIGHT_BLACK | nombre **o hex** | ✅ | SHOULD | Clave `WARNING` (con `WARN` falla todo el YAML). |
| `colorConfig.textStyles` | Map&lt;LogLevel, BOLD\|UNDERLINE&gt; | vacío | — | ✅ | LATER | — |

### 2.3 `masking`

| Clave | Tipo | Default código | Default spec | Estado | v1 | Notas |
|---|---|---|---|---|---|---|
| `enabled` | boolean | `true` | `true` | ✅ | MUST | — |
| `character` | char | `-` | `*` | ✅ | MUST | La spec la llama `char`. Default `*`. |
| `defaultType` | MaskType | `FULL` | `FULL` | ⚪ muerta | MUST | Usarla para MSK-09 (por nombre de campo). |
| `defaultLength` | Integer | `null` | — | 🟡 | MUST | null → NPE en A. |
| `strategies` | Map&lt;nombre, FQCN&gt; | `null` | — | ✅ | SHOULD | La spec también menciona `strategyClass` (único) — descartar. |
| `deepScanPackages` | List&lt;String&gt; | `null` | — | ✅ solo B | MUST | — |
| `defaultMaxDepth` | Integer | `null` | — | ✅ solo B | MUST | null → NPE. |
| `fields` *(nueva)* | List&lt;String&gt; | — | — | — | MUST | MSK-09. |
| `patterns` *(nueva)* | List&lt;regex&gt; | — | — | — | SHOULD | MSK-10. |

### 2.4 Anotaciones (configuración en código)

| Elemento | Atributos | Defaults | Estado | v1 |
|---|---|---|---|---|
| `@MaskedLogField` | `type`, `length`, `strategy` | `length` = 4 (A) / 1 (B) | 🟡 | MUST |
| `@DeepMasking` | `maxDepth` | `1` | 🟡 B | MUST (o reemplazar por recorrido automático) |
| `@LogEnrichment` | `captureRequest`, `captureResponse`, `captureHeaders`, `captureQueryParams`, `captureRequestBody`, `captureResponseBody`, `includeHeaders`, `excludeHeaders` | true, true, true, true, false, false, {}, {} | 🔴 B | DROP? / LATER |

### 2.5 Otros mecanismos

| Mecanismo | Valor | Estado | v1 |
|---|---|---|---|
| System property `pragma.log.forceColors` | `true` | ✅ | SHOULD |
| Headers fijos `X-Request-Id` / `X-Trace-Id` | Si falta, requestId = UUID | ✅ A | SHOULD (hacerlos configurables) |
| Propiedad Spring `pragma.log.config.formatter` en `logback-spring.xml` | `JSON` | Solo en spec | DROP (lo resuelve el starter) |
| Encoders en XML: `co.pragma.logging.{java,spring,reactive}...Encoder` | Sin atributos | ✅ A (3 copias idénticas) | MUST (uno solo) |

---

## Parte 3 — Contradicciones a resolver

| # | Tema | Versión 1 | Versión 2 | Propuesta |
|---|---|---|---|---|
| 1 | Naming de claves | Spec/manual: kebab-case (`formatter-options`, `max-size-text`) | Código: camelCase | camelCase en YAML propio; Spring acepta ambos con relaxed binding. |
| 2 | Raíz del YAML | Spec: `pragma-log-config:` | Código: sin raíz | Spring: `pragma.log.*`; archivo propio sin raíz. |
| 3 | Dónde se carga la config | Spec: "el core nunca carga config, lo hace cada integración" | Spec: "`LoggingConfigLoader.load()` desde classpath es universal" | Core recibe un objeto de config; cada integración decide la fuente (ADR-2). |
| 4 | `hooks`, `multithread`, `max-log-size` | Spec: dentro de `formatter-options` | Código: en la raíz | Raíz. |
| 5 | Carácter de masking | `char`, default `*` | `character`, default `-` | `character`, default `*`. |
| 6 | Interfaz de masking | Spec: `mask(String, MaskedLogField)` | Código: `mask(String, char, int)` | La del código (desacoplada de la anotación). |
| 7 | Interfaz de alertas | Spec: `onLargeLog(String,int)`, `onMaskingError(Exception)` | Código: `onLogSizeExceeded(event, actual, max)`, `onMaskingError(event, error)` | La del código. |
| 8 | Default de `maxLogSize` | Spec: 2000 | Código: 90000 | Decidir con datos (p. ej. 16 KB, límite habitual de shippers). |
| 9 | Nivel WARN | `LogLevel.WARNING` (código) | SLF4J/Logback: `WARN` | `WARN`. |
| 10 | Encoder | Spec: un único `SpringLogbackEncoder` | Código: 3 encoders idénticos | Un solo encoder en un módulo `logback`. |
| 11 | Masking de HIDDEN | A: `""` | B: `null` / campo eliminado | Eliminar el campo. |
| 12 | Longitud default de `@MaskedLogField` | A: 4 | B: 1 | Decidir (4 es más útil para PREFIX/SUFFIX). |
