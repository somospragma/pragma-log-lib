# Mapeo de campos a ELK, Datadog y OpenTelemetry

pragma-log escribe un esquema propio y estable (`timestamp`, `level`, `app`…). Cada plataforma de observabilidad espera otros nombres para reconocer el nivel, el servicio o la traza. Con `rename-fields` (y `$.a.b` para anidar) la salida queda en el esquema de cada una, sin código.

## Tabla de equivalencias

| Campo pragma-log | Elastic (ECS) | Datadog | OpenTelemetry (log data model) |
|---|---|---|---|
| `timestamp` | `@timestamp` | `timestamp` (o `date`) | `Timestamp` |
| `level` | `log.level` | `status` | `SeverityText` |
| `logger` | `log.logger` | `logger.name` | `InstrumentationScope.Name` |
| `thread` | `process.thread.name` | `logger.thread_name` | `thread.name` |
| `message` | `message` | `message` | `Body` |
| `app` | `service.name` | `service` | `service.name` (recurso) |
| `version` | `service.version` | `version` | `service.version` (recurso) |
| `host` | `host.name` | `host` | `host.name` (recurso) |
| `pid` | `process.pid` | `pid` | `process.pid` (recurso) |
| `env` (campo fijo) | `service.environment` | `env` | `deployment.environment.name` |
| `error.type` | `error.type` | `error.kind` | `exception.type` |
| `error.message` | `error.message` | `error.message` | `exception.message` |
| `stacktrace` | `error.stack_trace` | `error.stack` | `exception.stacktrace` |
| `requestId` | `http.request.id` | `http.request_id` | `http.request.id` |
| `traceId` | `trace.id` | `dd.trace_id` (ver nota) | `TraceId` |
| `spanId` | `span.id` | `dd.span_id` (ver nota) | `SpanId` |
| `http.method` | `http.request.method` | `http.method` | `http.request.method` |
| `http.path` | `url.path` | `http.url_details.path` | `url.path` |
| `http.status` | `http.response.status_code` | `http.status_code` | `http.response.status_code` |
| `http.durationMs` | `event.duration` (en ns, ver nota) | `duration` (en ns, ver nota) | `http.server.request.duration` (métrica) |
| `http.header.<nombre>` | `http.request.headers.<nombre>` | `http.headers.<nombre>` | `http.request.header.<nombre>` |
| `http.request.body` | `http.request.body.content` | `http.request.body` | `http.request.body` |
| `http.response.body` | `http.response.body.content` | `http.response.body` | `http.response.body` |

Notas:

- **Duración:** ECS y Datadog esperan la duración en nanosegundos; pragma-log la da en milisegundos (`http.durationMs`). Si renombras el campo, conviértelo en el pipeline de ingesta o búscalo con el nombre original.
- **Trazas en Datadog:** Micrometer Tracing escribe IDs hexadecimales de 128 bits (W3C). Datadog relaciona logs y trazas con `dd.trace_id` en decimal de 64 bits, salvo que el agente esté configurado para IDs W3C (`DD_TRACE_128_BIT_TRACEID_LOGGING_ENABLED=true`, el default en agentes recientes). Con el agente actual basta con renombrar.
- **OpenTelemetry:** los nombres de la tabla son los del modelo de logs; si envías JSON a un collector, usa el receptor `filelog` con un operador `json_parser` y mapea `level` a `severity`. Los campos de recurso (`service.name`, `host.name`) los suele poner el collector o el SDK.
- **Campos sin equivalente** (`error.rootCause`, `http.query.*`, campos propios): se quedan con su nombre y son buscables igual.
- **Los campos anidados** (`$.log.level`) y los nombres con puntos (`log.level`) son lo mismo para Elasticsearch y Datadog; se usa `$.` cuando el campo debe quedar dentro de un objeto que ya existe, como `stacktrace` dentro de `error`.

## Elastic (ECS)

```yaml
pragma:
  log:
    rename-fields:
      timestamp: "@timestamp"
      level: log.level
      logger: log.logger
      thread: process.thread.name
      app: service.name
      version: service.version
      host: host.name
      pid: process.pid
      env: service.environment
      stacktrace: $.error.stack_trace      # dentro del objeto error, junto a type y message
      requestId: http.request.id
      traceId: trace.id
      spanId: span.id
      http.method: http.request.method
      http.path: url.path
      http.status: http.response.status_code
    fields:
      env: prod
    timestamp:
      pattern: "[ISO_INSTANT]"             # ECS recomienda UTC
      zone: UTC
```

Resultado:

```json
{"@timestamp":"2026-11-10T15:04:05.123Z","log.level":"ERROR","log.logger":"com.acme.Orders","message":"Payment failed","service.name":"orders","service.environment":"prod","http.request.id":"req-7","trace.id":"4bf92f3577b34da6a3ce929d0e0e4736","error":{"type":"java.lang.IllegalStateException","message":"Gateway timeout","stack_trace":"java.lang.IllegalStateException: ..."}}
```

## Datadog

```yaml
pragma:
  log:
    rename-fields:
      level: status
      logger: logger.name
      thread: logger.thread_name
      app: service
      stacktrace: $.error.stack
      requestId: http.request_id
      traceId: dd.trace_id
      spanId: dd.span_id
      http.path: http.url_details.path
      http.status: http.status_code
    fields:
      env: prod                            # Datadog usa "env" tal cual
```

El tipo de excepción queda en `error.type`; para que Datadog lo muestre como `error.kind`, agrega un remapper en el pipeline de logs o déjalo como atributo buscable.

## OpenTelemetry

```yaml
pragma:
  log:
    rename-fields:
      level: severity_text
      app: service.name
      version: service.version
      host: host.name
      env: deployment.environment.name
      thread: thread.name
      stacktrace: exception.stacktrace
      traceId: trace_id
      spanId: span_id
      http.method: http.request.method
      http.path: url.path
      http.status: http.response.status_code
```

Collector con `filelog`:

```yaml
receivers:
  filelog:
    include: [/var/log/app/*.log]
    operators:
      - type: json_parser
        timestamp: {parse_from: attributes.timestamp, layout_type: gotime, layout: "2006-01-02T15:04:05.999999999Z07:00"}
        severity: {parse_from: attributes.severity_text}
      - type: trace_parser
        trace_id: {parse_from: attributes.trace_id}
        span_id: {parse_from: attributes.span_id}
      - type: move
        from: attributes.message
        to: body
```

## En Quarkus y logback.xml

Las mismas claves sirven en `application.properties` (`pragma.log.rename-fields.http.method=http.request.method`) y en `logback.xml` (`<renameField>level=log.level</renameField>`, una por campo). Los nombres con puntos funcionan tal cual, sin corchetes. El mapeo ECS de esta página está probado en los tests del starter.
