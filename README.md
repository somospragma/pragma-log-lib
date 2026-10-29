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

## Módulos

| Módulo | Contenido |
|---|---|
| `pragma-log-core` | Esquema de campos, configuración y (próximamente) masking. Sin dependencias de framework. |
| `pragma-log-logback` | Encoder JSON para Logback, construido sobre logstash-logback-encoder. |
| `pragma-log-bom` | Alinea las versiones de todos los módulos. |
| `examples/plain-java` | App de ejemplo (no se publica). |

Próximos: `pragma-log-spring-boot-starter` (MVC y WebFlux) y `pragma-log-quarkus`.

## Desarrollo

Requiere JDK 17 o superior.

```bash
./gradlew build                        # compila y corre los tests
./gradlew :examples:plain-java:run     # ejecuta el ejemplo
./gradlew publishToMavenLocal          # publica en ~/.m2 para probar en otro proyecto
```
