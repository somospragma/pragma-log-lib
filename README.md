# pragma-log

Librería de logging estructurado para servicios Java de Pragma. Sigues usando SLF4J (`log.info(...)`) y la librería produce logs JSON homogéneos, enriquecidos y con datos sensibles enmascarados.

> **Estado:** relanzamiento v2 en construcción (`0.1.0-SNAPSHOT`). Todavía no hay una versión publicada.
> Requerimientos y decisiones: [docs/relanzamiento](docs/relanzamiento/).
> La implementación anterior está en las ramas `legacy/libs` y `legacy/nexus`.

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
    <encoder class="co.com.pragma.logging.logback.PragmaJsonEncoder">
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
{"timestamp":"2026-10-29T17:14:02.413-05:00","message":"Order placed","logger":"com.acme.Orders","thread":"main","level":"INFO","requestId":"req-123","orderId":"ORD-9","app":"my-service"}
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

**Por nombre**, sin anotación (clases de terceros, mapas, MDC, key-values). Por defecto: `password`, `secret`, `token`, `accessToken`, `apiKey`, `authorization`, `cardNumber`, `cvv`, entre otros. Se ignoran mayúsculas y separadores: `access_token` = `accessToken`.

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

## Campos y excepciones

| Propiedad del encoder | Ejemplo | Descripción |
|---|---|---|
| `appName` | `orders-service` | Valor del campo `app`. |
| `field` | `env=prod` | Campo fijo en todos los eventos; se puede repetir. |
| `hideFields` | `thread,internalId` | Oculta campos estándar, fijos, de MDC o key-values. |
| `renameField` | `level=severity` | Renombra cualquier campo; se puede repetir. |
| `includeStacktrace` | `false` | Sin stack trace; se mantiene el resumen `error`. Default `true`. |
| `stacktraceMaxDepth` | `30` | Frames por excepción de la cadena. Default `50`. |
| `stacktraceMaxLength` | `8000` | Caracteres máximos del stack trace. Default `16384`. |
| `stacktraceRootCauseFirst` | `true` | Escribe primero la causa raíz. Default `false`. |

Cuando se loguea una excepción, además del `stacktrace` sale un resumen fácil de buscar:

```json
"error":{"type":"java.lang.IllegalStateException","message":"Gateway timeout","rootCause":"java.net.SocketTimeoutException: Read timed out"}
```

## Módulos

| Módulo | Contenido |
|---|---|
| `pragma-log-core` | Esquema de campos, configuración y masking (sobre Jackson 3). Sin dependencias de framework. |
| `pragma-log-logback` | Encoder JSON para Logback, construido sobre logstash-logback-encoder. |
| `pragma-log-bom` | Alinea las versiones de todos los módulos. |
| `examples/plain-java` | App de ejemplo (no se publica). |
| `benchmarks` | Benchmarks JMH (no se publica). Ver [rendimiento](docs/relanzamiento/04-rendimiento.md). |

Próximos: `pragma-log-spring-boot-starter` (MVC y WebFlux) y `pragma-log-quarkus`.

## Desarrollo

Requiere JDK 17 o superior.

```bash
./gradlew build                        # compila y corre los tests
./gradlew :examples:plain-java:run     # ejecuta el ejemplo
./gradlew publishToMavenLocal          # publica en ~/.m2 para probar en otro proyecto
```
