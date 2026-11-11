# pragma-log

Librería de logging estructurado para servicios Java de Pragma. Sigues usando SLF4J (`log.info(...)`) y la librería produce logs JSON homogéneos, enriquecidos y con datos sensibles enmascarados.

> **Estado:** relanzamiento v2 en construcción (`0.1.0-SNAPSHOT`). Todavía no hay una versión publicada.
> Requerimientos y decisiones: [docs/relanzamiento](docs/relanzamiento/).
> La implementación anterior está en las ramas `legacy/libs` y `legacy/nexus`.

## Uso rápido (Spring Boot 3.5+ / 4.x, MVC y WebFlux)

Solo la dependencia. No hace falta `logback-spring.xml`:

```kotlin
dependencies {
    implementation("co.com.pragma.logging:pragma-log-spring-boot-starter:0.1.0-SNAPSHOT")
}
```

Cada log sale en JSON con masking, `app` = `spring.application.name`, `host` (en Kubernetes, el nombre del pod) y el contexto HTTP del request (`requestId`, `http.method`, `http.path`). El `requestId` se toma del header `X-Request-Id`; si no viene o no es seguro se genera uno, y se devuelve en la respuesta. Si la app usa Micrometer Tracing (Brave u OpenTelemetry), `traceId` y `spanId` aparecen solos, también en WebFlux.

Al terminar cada request sale un log con el status y la duración:

```json
{"message":"HTTP GET /orders/7 200 in 12 ms","logger":"co.com.pragma.logging.http","http.status":200,"http.durationMs":12,"requestId":"req-7","http.method":"GET","http.path":"/orders/7",...}
```

Las excepciones no manejadas salen con `500` y las de Spring con su status (`404`, `400`…). Las rutas `/actuator/**` no lo generan.

```yaml
pragma:
  log:
    version: 1.4.2          # campo "version"; por defecto spring.application.version
    include-pid: true       # campo "pid" (apagado por defecto); include-host: false quita "host"
    fields:                 # campos fijos
      env: prod
    hide-fields: [thread]
    rename-fields:
      level: severity
      requestId: $.http.request.id     # un nombre $.a.b anida el campo
    field-order: [timestamp, level, message]
    required-fields: [requestId]       # si falta, sale "unknown" y se avisa
    extra-field: extra                 # agrupa MDC y key-values en "extra"
    masking:
      fields: [password, documentId]   # reemplaza la lista por defecto
      patterns: [CARD, JWT]            # busca y enmascara en el texto (ver "Red de seguridad")
    max-field-length: 4000             # trunca mensajes y valores de texto largos
    alerts:
      max-log-size: 64KB               # avisa cuando un log supera ese tamaño
      truncate: true                   # y además lo recorta hasta que quepa
    stacktrace:
      max-depth: 30
    http:
      headers: [X-Client-Id]           # headers a incluir; "*" = todos menos exclude-headers (credenciales)
      query-params: [page, channel]    # salen como http.query.page; "*" = todos
      request-log:
        entry: true                    # además, un log al iniciar cada request
        exclude-paths: [/actuator/**, /health]   # enabled: false lo apaga
      body:
        enabled: true                  # bodies en el log de fin de request, enmascarados (apagado por defecto)
        max-size: 4KB
    enrichers: [com.acme.TenantEnricher]   # o como bean de Spring
    async:
      enabled: true                    # log.info(...) no espera la escritura
    context-keys: [userId, tenantId]   # WebFlux: valores del Reactor Context que salen en los logs
    timestamp:
      zone: America/Bogota             # pattern: [ISO_INSTANT] o yyyy-MM-dd HH:mm:ss.SSS
    # format: xml     # json (default), xml, csv, custom o plain (ver "Formatos y distribución de campos")
    # format: plain   # texto legible con el mismo masking, para local (ver "Texto plano y colores")
    # enabled: false  # apaga la librería
```

**WebFlux:** funciona igual. El contexto del request viaja en el Reactor Context y aparece en los logs aunque el código cambie de hilo (`delayElement`, `publishOn`, llamadas no bloqueantes), sin `block()` ni ThreadLocals manuales. Para eso el starter activa la propagación automática de contexto de Reactor (equivale a `spring.reactor.context-propagation=auto`).

Tus propias variables también: declara las claves en `pragma.log.context-keys` y ponlas en el Reactor Context como siempre.

```java
return userService.find(id)
        .doOnNext(user -> log.info("User loaded"))      // sale con "userId":"u-7","tenantId":"42"
        .contextWrite(Context.of("userId", "u-7", "tenantId", 42));
```

Funciona en cualquier flujo reactivo (también fuera de un request HTTP, p. ej. consumidores de colas), no se mezcla entre requests y no queda pegado después. Se enmascara por nombre igual que el resto (una clave `token` sale enmascarada). En Spring MVC no hace falta: usa `MDC.put("userId", id)`.

El IDE autocompleta todas las propiedades `pragma.log.*`. Una configuración inválida detiene el arranque con un mensaje claro.

## Uso rápido (Quarkus 3.33+)

Solo la dependencia:

```kotlin
dependencies {
    implementation("co.com.pragma.logging:pragma-log-quarkus:0.1.0-SNAPSHOT")
}
```

Los logs de consola y archivo salen en JSON con el mismo esquema, el mismo masking y las mismas propiedades que en Spring, ahora en `application.properties`:

```properties
pragma.log.fields.env=prod
pragma.log.rename-fields.level=severity
pragma.log.masking.fields=password,documentId
pragma.log.http.headers=X-Client-Id
pragma.log.http.request-log.exclude-paths=/q/**,/health
pragma.log.timestamp.zone=America/Bogota
%dev.pragma.log.format=plain   # texto legible con el mismo masking en dev mode
```

- Funciona con SLF4J (`log.info("{}", obj)`, `log.atInfo().addKeyValue(...)`) y con JBoss Logging (`Log.infof("%s", obj)`). La extensión reemplaza el binding SLF4J de Quarkus, que convertía los objetos a texto antes de poder enmascararlos.
- Cada request agrega `requestId`, `http.method` y `http.path`, también en los hilos worker y en código reactivo con Mutiny. Al terminar sale el log con `http.status` y `http.durationMs`; las rutas `/q/**` no lo generan.
- `app` y `version` salen de `quarkus.application.name` y `quarkus.application.version`.
- Probado en JVM, jar de producción, dev mode y **modo nativo** (GraalVM/Mandrel; un job de CI compila el ejemplo y le corre los tests).
- **Modo nativo:**
  - Las clases con `@MaskedLogField`, los `LogAlertListener` y las `MaskingStrategy` de la app se registran solas para reflexión.
  - Otras clases que se logueen como objeto necesitan `@RegisterForReflection`; si no, salen como `<ClassName>`, sin romper el log.

## Uso rápido (Java puro + Logback)

```kotlin
dependencies {
    implementation("co.com.pragma.logging:pragma-log-logback:0.1.0-SNAPSHOT")
}
```

`src/main/resources/logback.xml`:

```xml
<configuration>
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="co.com.pragma.logging.logback.PragmaStructuredEncoder">
      <appName>my-service</appName>
    </encoder>
  </appender>
  <root level="INFO">
    <appender-ref ref="STDOUT"/>
  </root>
</configuration>
```

```java
MDC.put("requestId", "req-123");
log.atInfo().addKeyValue("orderId", "ORD-9").log("Order placed");
```

```json
{"timestamp":"2026-10-29T17:14:02.413-05:00","message":"Order placed","logger":"com.acme.Orders","thread":"main","level":"INFO","requestId":"req-123","orderId":"ORD-9","app":"my-service","host":"orders-7d9f-x2k"}
```

## Enmascaramiento de datos sensibles

Todo objeto que llega al log pasa por masking: en el `{}` del mensaje, en los key-values y en el MDC. Nunca se usa `toString()` y el objeto original no se modifica.

**Por anotación**, en cualquier tipo de campo (también heredados y en records):

```java
record Customer(
        String name,
        @MaskedLogField(type = MaskType.MIDDLE, length = 4) String cardNumber,
        @MaskedLogField(type = MaskType.HIDDEN) String cvv) {}
```

**En una clase**, la anotación enmascara cada uno de sus campos de la misma forma; un campo con su propia anotación usa la suya. Las subclases la heredan:

```java
@MaskedLogField(type = MaskType.MIDDLE, length = 2)
class ContactData {
    String phone;                                   // MIDDLE, como la clase
    String email;                                   // MIDDLE, como la clase
    @MaskedLogField(type = MaskType.HIDDEN) String note;   // el campo manda
}
```

**Por nombre**, sin anotación (clases de terceros, mapas, MDC, key-values, headers). Por defecto: `password`, `secret`, `token`, `accessToken`, `apiKey`, `authToken`, `authorization`, `cookie`, `cardNumber`, `cvv`, entre otros. Se ignoran mayúsculas y separadores: `access_token` = `accessToken`. En nombres con puntos cuenta el último tramo, sin el prefijo `x-`: `http.header.x-api-key` se enmascara como `apiKey`.

```
log.info("Customer registered: {}", customer);
→ "message":"Customer registered: {\"name\":\"Ana\",\"cardNumber\":\"4111********1111\",\"password\":\"******\"}"
```

| Tipo | Ejemplo (`length = 4`) |
|---|---|
| `FULL` (default) | `password` → `********` |
| `PREFIX` | `secret123` → `****et123` |
| `SUFFIX` | `secret123` → `secre****` |
| `MIDDLE` | `4111111111111111` → `4111********1111` |
| `HIDDEN` | el campo no aparece |
| `CUSTOM` | estrategia propia registrada por nombre |

Opciones del encoder en `logback.xml` (todas opcionales):

| Propiedad | Default | Descripción |
|---|---|---|
| `maskingEnabled` | `true` | Activa o desactiva todo el masking. |
| `maskCharacter` | `*` | Carácter de máscara. |
| `maskDefaultType` | `FULL` | Tipo aplicado a los campos enmascarados por nombre. |
| `maskDefaultLength` | `4` | Longitud cuando la anotación no la define. |
| `maskFields` | lista segura | Nombres separados por coma; **reemplaza** la lista por defecto. |
| `maskMaxDepth` | `10` | Profundidad máxima de objetos anidados. |
| `maskStrategy` | — | `NOMBRE=com.acme.MiEstrategia`; se puede repetir. |

Si la configuración es inválida, se reporta en el estado de Logback y se usan los valores seguros por defecto. Si el masking de un evento falla, el log se escribe igual, sin argumentos y con el campo `pragmaLogError`.

### Red de seguridad: patrones, truncado y alertas

Las anotaciones y los nombres no ven datos metidos dentro de un texto (`"cobrado a la tarjeta " + numero`). Para eso está `masking.patterns`, que busca en el mensaje, en los valores de texto de MDC y key-values (también dentro de objetos), en el stack trace y en el resumen `error`:

| Patrón | Qué enmascara | Resultado |
|---|---|---|
| `CARD` | Números de tarjeta de 13 a 19 dígitos (con espacios o guiones) que pasan la validación Luhn | `4111 **** **** 1111` |
| `EMAIL` | La parte local del correo, menos la primera letra | `a********@acme.com` |
| `JWT` | El token completo | `********` |
| una regex | Lo que coincida; si tiene un grupo, solo el grupo: `password=(\S+)` | `password=*******` |

Cada patrón recorre todos los logs, así que mantén la lista corta. Está apagado por defecto.

- `max-field-length`: corta el mensaje y cada valor de texto largo con `...[truncated N chars]`. Primero se enmascara y después se corta, así ningún pedazo de un secreto queda visible. El stack trace tiene su propio límite.
- `alerts.max-log-size`: cuando un log supera ese tamaño (bytes UTF-8 ya codificado), se avisa. El log se escribe completo, salvo que actives `alerts.truncate: true`: entonces se recortan sus campos más grandes (los textos conservan el comienzo, los objetos se reemplazan por una nota) hasta que quepa, y el log lleva `"truncated":true`. `timestamp`, `level`, `logger`, `thread` y `app` nunca se recortan. Aplica a JSON, XML, CSV y formatos propios.
- Avisos: por defecto una línea en la salida de error, máximo una por minuto por logger (`alerts.console: false` la apaga). Para llevarlos a una métrica o una notificación, implementa `LogAlertListener` (`onLargeLog`, `onMaskingError`, que avisa cuando un log no se pudo enmascarar, y `onMissingFields`, cuando a un log le faltan campos obligatorios). Regístralo como bean de Spring, en `alerts.listeners` con el nombre de la clase, o con `LogAlerts.register(...)`. Si hay appender de consola y de archivo, cada uno avisa por su lado.

En `logback.xml`: `<maskPattern>CARD</maskPattern>` (se puede repetir), `<maxFieldLength>`, `<maxLogSize>` (bytes), `<alertListener>` (se puede repetir) y `<alertConsole>`. Un patrón o un listener inválidos detienen el arranque en Spring y Quarkus.

## Campos y excepciones

| Propiedad del encoder | Ejemplo | Descripción |
|---|---|---|
| `appName` | `orders-service` | Valor del campo `app`. |
| `appVersion` | `1.4.2` | Valor del campo `version`; sin valor, no sale. |
| `includeHost` | `false` | Quita el campo `host` (variable `HOSTNAME` o nombre del equipo). Default `true`. |
| `includePid` | `true` | Agrega el campo `pid`. Default `false`. |
| `field` | `env=prod` | Campo fijo en todos los eventos; se puede repetir. |
| `hideFields` | `thread,internalId` | Oculta campos estándar, fijos, de MDC o key-values. |
| `renameField` | `level=severity` | Renombra cualquier campo; se puede repetir. |
| `includeStacktrace` | `false` | Sin stack trace; se mantiene el resumen `error`. Default `true`. |
| `stacktraceMaxDepth` | `30` | Frames por excepción de la cadena. Default `50`. |
| `stacktraceMaxLength` | `8000` | Caracteres máximos del stack trace. Default `16384`. |
| `stacktraceRootCauseFirst` | `true` | Escribe primero la causa raíz. Default `false`. |
| `timestampPattern` | `[ISO_INSTANT]` | Patrón de `DateTimeFormatter` o una constante entre corchetes. Default `[ISO_OFFSET_DATE_TIME]`. |
| `timeZone` | `UTC` | Zona horaria del timestamp. Default: la del sistema. |

Ningún log sale sin mensaje: si viene vacío, el mensaje es la excepción logueada (`java.lang.IllegalStateException: boom`) o, si no hay, `(empty message)`.

Cuando se loguea una excepción, además del `stacktrace` sale un resumen fácil de buscar:

```json
"error":{"type":"java.lang.IllegalStateException","message":"Gateway timeout","rootCause":"java.net.SocketTimeoutException: Read timed out"}
```

## Contexto HTTP, bodies y enriquecimiento

**Headers y query params.** `http.headers` es una lista permitida; con `"*"` entran todos los headers menos los de `http.exclude-headers` (por defecto `Authorization`, `Proxy-Authorization`, `Cookie` y `Set-Cookie`). Un header nombrado explícitamente siempre entra y, si su nombre es sensible, sale enmascarado. `http.query-params` hace lo mismo con los parámetros de la URL (`http.query.page`); nunca se lee el body de un formulario para obtenerlos.

**Log de inicio.** Con `http.request-log.entry: true` cada request escribe también `HTTP POST /orders started` al comenzar, con el mismo contexto.

**Bodies.** Con `http.body.enabled: true` el log de fin de request lleva `http.request.body` y `http.response.body`:

```json
{"message":"HTTP POST /orders 200 in 35 ms","http.request.body":{"amount":10,"password":"****"},"http.response.body":{"status":"created","token":"*****"},...}
```

- Los bodies JSON y de formulario se convierten en objetos y se enmascaran como cualquier objeto logueado: por nombre de campo a cualquier profundidad y por patrones. En Quarkus la respuesta es el objeto que retorna el endpoint, así que también aplican sus `@MaskedLogField`. Otros textos (XML, texto plano) solo pasan por los patrones.
- Se guardan los primeros `max-size` bytes (4 KB por defecto); el resto se corta con `...[truncated N bytes]`. Los bodies se copian a medida que pasan, sin leerlos por adelantado ni guardarlos completos en memoria. Solo se capturan los tipos de `content-types` (JSON, formularios, XML, `text/*`); nunca binarios.
- En Quarkus aplica a los endpoints de Quarkus REST.

**Enrichers.** Para agregar campos propios a todos los logs, implementa `LogEnricher` (`event.put("tenantId", ...)`). Regístralo como bean de Spring o CDI, en `enrichers` con el nombre de la clase, o con `LogEnrichers.register(...)`. Sus campos se enmascaran como los key-values, y un MDC o key-value con el mismo nombre gana.

**`@LogEnrichment` y `@LogField`.** Agregan campos a todos los logs escritos mientras corre un método de un bean de Spring o CDI, incluidos los de las clases que llama:

```java
@LogEnrichment("domain=payments")              // en la clase: aplica a todos sus métodos
class PaymentService {
    @LogEnrichment("operation=refund")
    Mono<Refund> refund(@LogField("paymentId") String id) {   // los logs llevan domain, operation y paymentId
        ...
    }
}
```

Los valores simples se escriben tal cual y los objetos como JSON enmascarado. Al terminar el método se restauran los valores anteriores del MDC. Con `Mono`, `Flux` o `Uni` los campos viajan hasta que el flujo termina, en cualquier hilo. En Spring solo hace falta spring-aop (viene con Spring), no AspectJ.

## Cambios de configuración en caliente y advertencias

**Sin reiniciar.** En Spring Boot, los cambios a `pragma.log.*` se aplican solos cuando Spring Cloud publica un `EnvironmentChangeEvent` (endpoint `refresh`, ConfigMaps de Kubernetes, Config Server). Si cambias el `Environment` de otra forma, llama `PragmaLogReloader.reload()` (es un bean). Aplica a masking, campos, distribución, alertas, stack trace, timestamp y contexto HTTP; pasar de `plain` a un formato estructurado sigue pidiendo reinicio. Si la configuración nueva es inválida, se mantiene la anterior y se avisa por la salida de error. En Java puro usa `<configuration scan="true">` en `logback.xml`: Logback relee el archivo y crea encoders nuevos. Quarkus no relee su configuración en caliente.

**Advertencias.** Al arrancar se avisa, una sola vez y sin detener la app, de combinaciones que no hacen lo que parecen. Algunos ejemplos: `json.pretty` con otro formato o en producción, `csv.*` sin `format: csv`, `alerts.truncate` sin `max-log-size`, un campo obligatorio que también está oculto, dos campos renombrados al mismo nombre, `masking.enabled: false` con patrones o bodies, `masking.fields: []`, o `http.headers: "*"` sin `exclude-headers`. En Spring salen por la salida de error, en Quarkus como WARN y en `logback.xml` en el estado de Logback.

## Logging asíncrono

Con logging asíncrono la llamada `log.info(...)` vuelve de inmediato y otro hilo enmascara, formatea y escribe el log.

- **Spring Boot:** `pragma.log.async.enabled: true`. Envuelve los appenders de consola y archivo de Spring Boot. Opciones: `queue-size` (8192), `never-block` (false) y `max-flush-time` (5s).
- **Java puro:** `PragmaAsyncAppender` en `logback.xml`, con `<appender-ref>` al appender real.
- **Quarkus:** usa el async propio de Quarkus (`quarkus.log.console.async=true`, `quarkus.log.file.async=true`); el formato de pragma-log se aplica igual.

```xml
<appender name="ASYNC" class="co.com.pragma.logging.logback.PragmaAsyncAppender">
  <appender-ref ref="CONSOLE"/>
</appender>
```

- **No se pierde nada:** a diferencia del `AsyncAppender` por defecto de Logback, no descarta logs `INFO`/`DEBUG` cuando la cola se llena. Quien loguea espera (`never-block: true` cambia esto y descarta). Al apagar la app se escriben los pendientes.
- **El contexto es el del hilo que loguea:** el MDC y los campos de los enrichers registrados como bean o en código se toman en el hilo que llamó, no en el que escribe. En Quarkus los enrichers corren en el hilo que escribe, así que usa MDC para datos del request.
- **Los objetos se enmascaran en el hilo que escribe:** no modifiques un objeto después de loguearlo.

## Formatos y distribución de campos

`format` elige la salida; todas pasan por el mismo masking y la misma distribución de campos:

| `format` | Salida |
|---|---|
| `json` (default) | Un objeto por línea. Con `json.pretty: true`, indentado en varias líneas (solo para leer en local: los recolectores de logs esperan un evento por línea). |
| `xml` | Un `<event>` por línea: `<event><timestamp>…</timestamp><level>INFO</level>…</event>`. Los objetos son elementos anidados. |
| `csv` | Columnas fijas (`csv.columns`, por defecto `timestamp, level, logger, thread, message`), separador `csv.delimiter` y encabezado opcional (`csv.header`, solo en `logback.xml` y Quarkus). Los objetos van como JSON y los saltos de línea como `\n`. Un campo anidado es una columna con puntos: `http.request.id`. |
| `custom` | Tu clase en `custom-formatter`: implementa `LogEventFormatter` (recibe los campos ya enmascarados y ordenados, devuelve el texto). Si falla, ese evento sale en JSON. |
| `plain` | Texto legible para local (ver la sección siguiente). |

Distribución de campos, igual en todos los formatos:

- **Ordenar:** `field-order` pone esos campos primero, en ese orden (por nombre original o renombrado); el resto conserva su orden.
- **Anidar:** un nombre nuevo que empieza con `$.` anida el campo: `rename-fields: {requestId: $.http.request.id}` → `"http":{"request":{"id":"req-7"}}`. También sirve en `extra-field`.
- **Obligatorios:** `required-fields` lista los campos que todo log debe tener. Si falta uno, sale con `required-placeholder` (`unknown` por defecto) y se avisa a los listeners de alertas (`onMissingFields`). El log nunca se pierde.
- **Objeto `extra`:** con `extra-field: extra`, el MDC y los key-values van dentro de `extra`, salvo los que nombres en `field-order`, `rename-fields` o `required-fields`, que quedan en la raíz. Los campos estándar y fijos siguen en la raíz.

```json
{"level":"INFO","message":"Order paid","timestamp":"…","http":{"request":{"id":"req-7"}},"extra":{"orderId":"o-1","amount":10},"app":"orders-service","userId":"unknown"}
```

En `logback.xml` usa `PragmaStructuredEncoder` (`PragmaJsonEncoder` sigue funcionando y escribe JSON) con `format`, `pretty`, `csvColumns`, `csvDelimiter`, `csvHeader`, `customFormatter`, `fieldOrder`, `requiredFields`, `requiredPlaceholder` y `extraField`.

## Texto plano y colores (para local)

`format: plain` cambia el JSON por una línea legible. El masking es el mismo: ningún secreto que el JSON oculte aparece en el texto.

```
2026-11-02 10:15:30.123  INFO [main] c.c.p.l.e.quarkus.CustomerResource : Customer found: {"id":"1","cardNumber":"4111********1111"} requestId=req-1
```

- Los campos de MDC y key-values van al final como `nombre=valor`; el stack trace, acotado, en las líneas siguientes. `app`, `host` y los campos fijos no salen en texto.
- En la consola el nivel sale con color (el timestamp tenue y el logger en cian). Los archivos de log nunca llevan colores.
- `color.mode`: `auto` (por defecto: solo en una terminal y nunca con la variable `NO_COLOR`), `always` (para la consola del IDE, que no se detecta como terminal) o `never`.
- `color.levels`: color por nivel, con nombre (`red`, `bright_blue`, `faint`…) o hex (`#ff8800`).
- `timestamp.pattern` y `timestamp.zone` aplican a los dos formatos. En texto el patrón por defecto es `yyyy-MM-dd HH:mm:ss.SSS`.
- Para usar texto en local y JSON en los demás ambientes, usa perfiles: `application-local.yml` en Spring, `%dev.` en Quarkus.

```yaml
pragma:
  log:
    format: plain
    color:
      mode: always
      levels:
        INFO: blue
        WARN: "#ff8800"
```

En Java puro, usa `PragmaTextEncoder` en `logback.xml`. Acepta las mismas propiedades de masking y stack trace que el JSON, más `color`, `levelColor` (`INFO=blue`, se puede repetir), `timestampPattern`, `timeZone` y `hideFields`. Pon `<color>NEVER</color>` en los appenders de archivo.

## Módulos

| Módulo | Contenido |
|---|---|
| `pragma-log-core` | Esquema de campos, configuración y masking (sobre Jackson 3). Sin dependencias de framework. |
| `pragma-log-logback` | Encoder JSON para Logback, construido sobre logstash-logback-encoder. |
| `pragma-log-spring-boot-starter` | Auto-configuración para Spring Boot 3.5+ y 4.x, MVC y WebFlux. Probado contra ambas versiones en cada build. |
| `pragma-log-quarkus` | Extensión de Quarkus 3.33+ (JBoss LogManager): JSON, masking y contexto HTTP. |
| `pragma-log-quarkus-deployment` | Parte de build de la extensión; Quarkus la usa sola. |
| `pragma-log-quarkus-slf4j` | Binding SLF4J de la extensión; conserva los argumentos para enmascararlos. |
| `pragma-log-bom` | Alinea las versiones de todos los módulos (y `jackson-annotations`). |
| `examples/plain-java` | App de ejemplo Java puro (no se publica). |
| `examples/spring-mvc` | App de ejemplo Spring Boot MVC (no se publica). |
| `examples/spring-webflux` | App de ejemplo Spring Boot WebFlux (no se publica). |
| `examples/quarkus` | App de ejemplo Quarkus con tests `@QuarkusTest` (no se publica). |
| `benchmarks` | Benchmarks JMH (no se publica). Ver [rendimiento](docs/relanzamiento/04-rendimiento.md). |

## Integración con ELK, Datadog y OpenTelemetry

[docs/mapeo-campos.md](docs/mapeo-campos.md) tiene la tabla de equivalencias y la configuración lista para que los logs lleguen con los nombres de Elastic (ECS), Datadog u OpenTelemetry.

## Versiones

Los cambios de cada versión están en [CHANGELOG.md](CHANGELOG.md). Se sigue versionado semántico: MAJOR cuando hay que tocar la app, MINOR para funcionalidad nueva compatible y PATCH para correcciones.

## Desarrollo

Requiere JDK 17 o superior (JDK 21+ para correr Error Prone).

```bash
./gradlew build                        # compila, analiza y corre los tests (lo mismo que la etapa Quality)
./gradlew :examples:plain-java:run     # ejecuta el ejemplo
./gradlew :examples:quarkus:quarkusDev # ejemplo Quarkus en dev mode
./gradlew :examples:quarkus:testNative -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true -Dquarkus.package.jar.enabled=false # ejemplo nativo (Docker con 8 GB)
./gradlew publishToMavenLocal          # publica en ~/.m2 para probar en otro proyecto
```

### Pipeline

`.github/workflows/pipeline.yml` corre en cada push y PR. Cada etapa empieza cuando la anterior pasa:

| Etapa | Jobs | Local |
|---|---|---|
| **Quality** | Lint con Error Prone (JDK 21, advertencias como error) · Compilación en Java 17 · Tests en Java 17 y 21 con cobertura mínima por módulo (75%; 50% en `pragma-log-quarkus`, que se prueba sobre todo con la app de ejemplo) · Código muerto con PMD · Tests en modo nativo de Quarkus | `./gradlew build boot3Test` |
| **Security** | Dependencias con OSV-Scanner (lo que cada módulo publica) · Secretos con gitleaks (todo el historial) · SAST con SpotBugs + FindSecBugs | ver abajo |
| **Build** | Jars, sources, javadoc y POMs de los 7 módulos, como artefacto `maven-artifacts` | `./gradlew publishToMavenLocal -Dmaven.repo.local=$PWD/build/m2` |
| **Approval** | Solo con tags `v*`: abre un issue y espera que alguien de la variable `RELEASE_APPROVERS` comente `approve` (o `deny`); cerrar el issue también rechaza. Expira en 2 horas | — |
| **Publish** | En el environment `maven-central` (solo admite tags `v*`): valida que los secretos y variables estén cargados y publica en Maven Central | — |

Para publicar se usan los secretos de la organización `somospragma` (los mismos que ya publican otras librerías de Pragma) y las variables del environment `maven-central` (Settings → Environments):

| Tipo | Nombre | Dónde | Qué va |
|---|---|---|---|
| Secreto | `OSSRH_USERNAME` / `OSSRH_PASSWORD` | Organización | Token de usuario de Maven Central (namespace `co.com.pragma`) |
| Secreto | `GPG_PRIVATE_KEY` / `GPG_PASSPHRASE` | Organización | Llave GPG de Pragma para firmar los artefactos y su contraseña |
| Variable | `MAVEN_GROUP_ID` | Environment | `co.com.pragma.logging` (dentro del namespace verificado `co.com.pragma`) |
| Variable | `POM_LICENSE_NAME` / `POM_LICENSE_URL` | Environment | Licencia del POM, p. ej. `Apache License 2.0` y su URL |

Los aprobadores del release están en la variable de repositorio `RELEASE_APPROVERS`. Para publicar: actualiza [CHANGELOG.md](CHANGELOG.md), crea el tag (`git tag v1.0.0 && git push origin v1.0.0`) y comenta `approve` en el issue que abre el pipeline.

Security en local (con Docker):

```bash
./gradlew resolveShippedDependencies --write-locks --no-configuration-cache
docker run --rm -v "$PWD":/src -w /src ghcr.io/google/osv-scanner:v2.3.0 scan source -L pragma-log-core/gradle.lockfile  # un -L por módulo
docker run --rm -v "$PWD":/repo ghcr.io/gitleaks/gitleaks:v8.30.1 git /repo --redact
./gradlew spotbugsMain
```

- Las excepciones aceptadas están en `config/spotbugs/exclude.xml` y `.gitleaksignore`, cada una con su motivo.
- Las reglas de código muerto están en `config/pmd/dead-code.xml`.
- Las actions y las imágenes Docker del pipeline están fijadas por digest.

