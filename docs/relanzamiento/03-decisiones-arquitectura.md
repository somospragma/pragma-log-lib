# pragma-log-lib — Decisiones de arquitectura (ADRs) v2

> Formato: contexto → decisión → consecuencias. Estado: **Aceptada** (confirmada por el dueño del proyecto), **Propuesta** (recomendación pendiente de validar) o **Requiere spike** (se valida con una prueba corta antes de construir).
> IDs de requerimientos: ver [01-catalogo-funcionalidades.md](01-catalogo-funcionalidades.md) y [02-requerimientos-v1.md](02-requerimientos-v1.md).

## Resumen

| # | Decisión | Estado |
|---|---|---|
| ADR-01 | JSON sobre **logstash-logback-encoder** (solo en el adaptador Logback) | Aceptada |
| ADR-02 | Baseline **Java 17**, CI también en 21 | Aceptada |
| ADR-03 | v1 incluye **Java puro, Spring MVC, WebFlux y Quarkus**, entregados en ese orden | Aceptada |
| ADR-04 | Publicación en **Maven Central** | Aceptada |
| ADR-05 | **groupId** verificable en Central | Propuesta — *decisión tuya* |
| ADR-06 | Arquitectura **hexagonal**: core agnóstico + adaptadores por backend de logging | Propuesta |
| ADR-07 | Masking como **módulo de Jackson** (serialización, sin mutar objetos) | Propuesta |
| ADR-08 | API del desarrollador = **SLF4J 2 estándar**, sin API propia | Propuesta |
| ADR-09 | Configuración nativa de cada framework (sin YAML propio) | Propuesta |
| ADR-10 | Contexto y trazas: **MDC** como único canal | Propuesta |
| ADR-11 | Quarkus como **extensión** sobre JBoss LogManager | Requiere spike |
| ADR-12 | **Jackson 3** en todo el proyecto; starter para Boot 3.x y 4.x | Aceptada (Jackson) · spike pendiente (Boot 3) |
| ADR-13 | Build: Gradle + version catalog + BOM | Propuesta |
| ADR-14 | Nueva rama `v2` desde cero; código viejo en ramas `legacy/*` | Propuesta |
| ADR-15 | La librería nunca rompe la app ni pierde un log | Propuesta |

---

## ADR-01 — JSON sobre logstash-logback-encoder · Aceptada

**Contexto.** El código actual reimplementa serialización JSON, stacktraces, MDC y argumentos. Eso es lo que más código consumió y lo que menos valor diferencial aporta. `logstash-logback-encoder` es el estándar de facto en Logback: providers componibles, MDC, key-values de SLF4J 2, stacktraces recortables, nombres de campo configurables y compatibilidad con async appenders.

**Decisión.** En el adaptador Logback, la salida JSON se construye con `LogstashEncoder`/`LoggingEventCompositeJsonEncoder` + **providers propios** de Pragma:
- `PragmaSchemaProvider`: campos del esquema Pragma (app, env, versión…), renombrado y ocultado (FLD-01..05).
- `MaskedArgumentsProvider` / `MaskedMessageProvider`: argumentos y mensaje pasan por masking (MSK-07).
- `PragmaContextProvider`: contexto HTTP y trazas desde MDC (CTX-*).

El formato CONSOLE se hace con un `PatternLayout`/converter de Logback, reutilizando el masking del core.

**Consecuencias.**
- ➕ Se elimina el pipeline propio (`LogFormatter`, `FieldSchemaOptimizer`, `TextTruncator`, `JsonFormatter`…).
- ➕ Opciones como stacktrace recortado (ERR-03), `maxFieldLength` (FLD-08) y renombrado vienen casi gratis.
- ➖ Dependencia externa en el adaptador Logback (no en el core). Hay que seguir sus versiones.
- ➖ Quarkus no usa Logback: ahí no aplica y necesita su propio adaptador (ADR-11). Por eso **el core no depende de logstash** (ADR-06).

## ADR-02 — Java 17 · Aceptada

**Decisión.** `release = 17` con Gradle toolchains. CI compila y prueba en 17 y 21 (y en la LTS más reciente disponible).

**Consecuencias.** Se pueden usar records, sealed, `switch` con patrones básicos y text blocks. No se usan virtual threads ni pattern matching de records en la API. El masking debe soportar records (SER-03).

## ADR-03 — Alcance de integraciones v1 · Aceptada

**Decisión.** La v1 incluye las cuatro integraciones, pero **se entregan en serie**, cada una terminada (con app de ejemplo y tests de integración) antes de empezar la siguiente:

| Orden | Integración | Release | Por qué en este orden |
|---|---|---|---|
| 1 | Java puro (Logback) | `0.1.0` | Valida core + masking + encoder sin framework. |
| 2 | Spring Boot MVC | `0.2.0` | Reutiliza el encoder; agrega auto-config y filtro HTTP. |
| 3 | Spring Boot WebFlux | `0.3.0` | Reutiliza el starter; agrega propagación de contexto reactivo. |
| 4 | Quarkus | `0.4.0` | Backend de logging distinto; se arranca con un spike. |
| — | Hardening, JMH, docs | `1.0.0` | — |

**Consecuencias.**
- ➕ Se evita repetir el problema anterior (4 integraciones a medias en paralelo).
- ➕ Cada `0.x` se publica y se puede probar en un servicio real.
- ➖ El primer equipo de Quarkus espera a `0.4.0`.
- **Regla:** no se empieza una integración mientras la anterior tenga MUST abiertos.

## ADR-04 — Maven Central · Aceptada

**Contexto.** "Librería pública de Gradle" = un artefacto en Maven Central; Gradle lo consume con `mavenCentral()`. (El Gradle Plugin Portal es solo para plugins de Gradle, no aplica.)

**Decisión.** Publicar en **Maven Central vía Central Portal** con el plugin `com.vanniktech.maven.publish`, desde GitHub Actions al crear un tag `vX.Y.Z`. Los `SNAPSHOT` se publican al repositorio de snapshots del Portal.

**Requisitos de Central (obligatorios):** sources jar, javadoc jar, firma GPG, POM con licencia, SCM, developers y URL.

**Consecuencias.**
- Hay que **elegir licencia** (propuesta: Apache-2.0) — *decisión de Pragma*.
- Secretos en GitHub Actions: usuario/token del Portal, llave GPG y passphrase.
- Lo publicado en Central **no se puede borrar**: nada se publica sin revisión (tags solo desde `main`).

## ADR-05 — groupId · Propuesta (decisión tuya)

**Contexto.** Central exige probar propiedad del namespace. El código usa `co.pragma.logging`, que corresponde al dominio `pragma.co`. El dominio de Pragma es **`pragma.com.co`**, cuyo namespace es `co.com.pragma`.

| Opción | Verificación | Comentario |
|---|---|---|
| `co.com.pragma.logging` | Registro TXT en el DNS de `pragma.com.co` | **Recomendada.** Necesita a alguien de infraestructura de Pragma. |
| `io.github.somospragma` | Repo público temporal en la org de GitHub | Sin DNS, pero queda atado a GitHub. |
| `co.pragma.logging` | Solo si Pragma controla `pragma.co` | Verificar antes de usarlo. |

**Decisión propuesta.** `co.com.pragma.logging`; los paquetes Java pasan a `co.com.pragma.logging.*` para coincidir.

## ADR-06 — Arquitectura hexagonal por backend · Propuesta

**Decisión.** Módulos de la v2:

```
pragma-log-bom                    → alinea versiones para el consumidor
pragma-log-core                   → modelo, masking, esquema, config (POJO), SPI. Solo depende de Jackson + SLF4J API
pragma-log-logback                → encoders/providers (logstash-logback-encoder) + layout CONSOLE
pragma-log-spring-boot-starter    → auto-config MVC y WebFlux (condicional), @ConfigurationProperties, filtros
pragma-log-quarkus                → extensión Quarkus (runtime + deployment), formatter JBoss
examples/                         → apps de ejemplo (no se publican)
```

- El **core no depende** de Logback, logstash, Spring ni Quarkus.
- Un solo módulo Spring para MVC y WebFlux (`@ConditionalOnWebApplication(type = SERVLET | REACTIVE)`), en vez de dos.
- No hay módulo `logging-java` aparte: Java puro = `pragma-log-logback`.

**Consecuencias.** 4 artefactos publicables + BOM, en lugar de 6. Las reglas (masking, esquema) se escriben una vez en el core y cada adaptador solo traduce el evento de su backend.

## ADR-07 — Masking como módulo de Jackson · Propuesta

**Contexto.** Los bugs más graves de hoy son de masking: muta el objeto, se salta campos no-String y heredados, filtra por `toString()` y usa reflexión por evento.

**Decisión.** El masking se aplica **al serializar**, como un `Module` de Jackson (`BeanSerializerModifier`) del core:
- Las propiedades con `@MaskedLogField` o cuyo nombre esté en `masking.fields` se serializan con la estrategia correspondiente. HIDDEN = la propiedad no se escribe.
- Jackson ya maneja herencia, colecciones, mapas, arrays, records, `Optional` y `java.time`, y cachea los serializadores por clase (cumple NFR-03 sin código propio).
- **Nunca se muta** el objeto original (MSK-08).
- Ciclos: se detectan con `IdentityHashMap` en un serializador envolvente y se emite `"[circular]"` (MSK-06).
- Los argumentos del mensaje (`{}`) se reemplazan por su representación enmascarada **antes** de formatear el mensaje (MSK-07).
- Masking por regex sobre el texto final (MSK-10) como paso aparte y opcional.
- `@DeepMasking` desaparece: el recorrido es siempre profundo; se limita con `masking.maxDepth`.

**Consecuencias.**
- ➕ Elimina `MaskingProcessor`, `TypeInspector` y la reflexión manual.
- ➖ Lo que no sea serializable por Jackson cae a un fallback seguro (`<ClassName>`), **nunca** a `toString()`.
- ➖ El `ObjectMapper` del masking es **propio y aislado**: no se registra en el `ObjectMapper` de la aplicación (no afecta las respuestas HTTP).

## ADR-08 — API del desarrollador: SLF4J 2 · Propuesta

**Decisión.** No hay API propia (`PragmaLogger` desaparece). El desarrollador usa:
- `log.info("Usuario {} creado", user)` → `user` sale enmascarado en el mensaje y como campo.
- `log.atInfo().addKeyValue("orderId", id).log("Orden creada")` → campos estructurados (API fluida estándar de SLF4J 2).
- `MDC.put(...)` → campos de contexto.

No se exige `StructuredArguments.kv(...)` de logstash, para que el código del servicio no dependa de Logback (y funcione igual en Quarkus vía slf4j-jboss).

## ADR-09 — Configuración nativa por framework · Propuesta

**Decisión.** El core expone un POJO inmutable `PragmaLogSettings` con validación. Cada adaptador lo llena desde su fuente nativa:

| Entorno | Fuente | Prefijo |
|---|---|---|
| Java puro | Propiedades del encoder en `logback.xml` (Joran) + overrides por system properties/env | `pragma.log.*` |
| Spring Boot | `application.yml` vía `@ConfigurationProperties` (con metadata para autocompletar en el IDE) | `pragma.log.*` |
| Quarkus | `application.properties` vía `@ConfigMapping` | `pragma.log.*` |

- Se elimina el YAML propio (`pragma-log-config.yml`), `LoggingConfigLoader` y `ObjectMergeHelper`.
- Mismas claves en todos los entornos (camelCase en YAML; Spring y Quarkus aceptan kebab-case).
- Config inválida → error claro al arrancar, nunca un fallback silencioso (CFG-05).
- En Spring, `appName` se toma de `spring.application.name`; en Quarkus de `quarkus.application.name`.

## ADR-10 — MDC como canal de contexto · Propuesta

**Decisión.** Todo lo contextual (traceId, spanId, requestId, datos HTTP, custom) llega al log **por MDC**:
- **Trazas:** Micrometer Tracing (Spring Boot 3+) y Quarkus OpenTelemetry ya escriben `traceId`/`spanId` en MDC. La librería solo los lee y los renombra si hace falta (CTX-01, casi sin código).
- **MVC:** `OncePerRequestFilter` que pone `requestId` y los headers en whitelist, y en `finally` **quita solo sus claves** (no `MDC.clear()` global).
- **WebFlux:** `WebFilter` + Reactor Context + `Hooks.enableAutomaticContextPropagation()` con `io.micrometer:context-propagation`, para que el MDC aparezca en cualquier hilo del pipeline.
- **Quarkus:** MDC de JBoss LogManager (propagado por Vert.x en contextos duplicados) + filtro JAX-RS.

**Consecuencias.** Se descartan `LogContextHolder`, los holders reactivos con `block()` y los aspectos AOP `@LogEnrichment` (ENR-05 → DROP). Las opciones de captura pasan a la config (`pragma.log.http.*`).

## ADR-11 — Quarkus como extensión · Requiere spike

**Contexto.** Quarkus usa JBoss LogManager, no Logback; ni el encoder ni logstash aplican.

**Propuesta.** Extensión Quarkus (módulos `runtime` + `deployment`) que registra un formatter JSON propio (o se integra con `quarkus-logging-json` mediante sus providers), reutilizando el masking y el esquema del core.

**Spike (1–2 días).** App Quarkus mínima que verifique: (1) masking de argumentos en el mensaje, (2) MDC con traceId de OTel, (3) funcionamiento en modo nativo (GraalVM): el masking basado en Jackson necesita registrar reflexión.

**Resultado esperado.** Elegir entre "extensión propia" o "providers sobre `quarkus-logging-json`".

## ADR-12 — Jackson 3 · Aceptada (spike pendiente para Boot 3)

**Contexto.** Al armar el esqueleto (2026-10-29) se verificó que **logstash-logback-encoder 9.0 ya usa Jackson 3** (`tools.jackson`, requiere Java 17). La línea 8.x sigue en Jackson 2. Spring Boot 4 también usa Jackson 3 por defecto.

**Decisión.** Todo el proyecto (core, masking, encoder) usa **Jackson 3** y logstash-logback-encoder 9.x. Así se evita nacer sobre una línea que el ecosistema está dejando.

**Consecuencias.**
- ➕ Alineado con Spring Boot 4 sin adaptaciones.
- ➖ En Spring Boot 3.x y Quarkus (Jackson 2) conviven ambas versiones en el classpath. Son paquetes distintos (`com.fasterxml.jackson` vs `tools.jackson`), así que no chocan, pero suman ~2 MB. Solo `jackson-annotations` se comparte.
- **Spike al iniciar E3:** app Spring Boot 3.x + starter, verificar convivencia y auto-config. Si hay conflictos, se evalúa soportar solo Boot 4 en `1.0`.

**Resultado del spike (2026-10-29).** Se soportan **Spring Boot 3.5 y 4.x**; el build corre todos los tests del starter contra ambas versiones (`test` y `boot3Test`).
- Con Gradle, Jackson 2.21 (Boot 3.5) y Jackson 3.2 conviven sin problemas.
- **Riesgo encontrado:** Jackson 3.2 necesita `jackson-annotations` ≥ 2.22. Con Maven, el BOM de Boot 3.5 la baja a 2.21 y la serialización lanza `NoClassDefFoundError`. Antes del arreglo, **ese error rompía el request de la aplicación**.
- Mitigaciones aplicadas:
  1. Masking y encoder capturan también `LinkageError`: el log sale con `<ClassName>` y la app sigue (ADR-15).
  2. Verificación al arranque que emite una advertencia con la solución.
  3. `pragma-log-bom` importa el BOM de Jackson 3, que fija `jackson-annotations` 2.22.
  4. README: con Maven y Boot 3.x, `jackson-bom.version=2.22.x` o importar `pragma-log-bom` antes.
- Boot 4 movió `EnvironmentPostProcessor` de paquete (la clave vieja queda deprecada para eliminación); el starter registra una implementación por cada clave.

## ADR-13 — Build · Propuesta

**Decisión.**
- Gradle (wrapper reciente) con `gradle/libs.versions.toml` como única fuente de versiones (NFR-07).
- Convention plugins en `build-logic/` (java-library, tests, publicación, javadoc).
- Lombok **no** se usa en código publicado (records de Java 17 bastan).
- JUnit 5 + AssertJ; Testcontainers solo si hace falta; JMH en un módulo `benchmarks` no publicado.
- CI en GitHub Actions: build + tests (Java 17 y 21) + cobertura (JaCoCo ≥ 80 % en core) en cada PR; publicación en tags.
- Tests de integración con las apps de `examples/`, capturando la salida y parseando el JSON.

## ADR-14 — Estrategia de repositorio · Propuesta

**Decisión.**
1. Commitear el estado actual de cada copia en ramas `legacy/libs` y `legacy/nexus` (incluido lo no commiteado) y subirlas.
2. Crear `v2` desde un commit vacío; cuando llegue a `0.1.0`, pasa a ser `main`.
3. Los documentos viejos (`SPEC.md`, `TASK_LIST.md`, etc.) quedan en las ramas legacy; en `v2` solo viven `docs/relanzamiento/*`.

## ADR-15 — Nunca romper ni perder · Propuesta

**Decisión.**
- Cualquier excepción dentro del masking/encoding se captura: se emite el log con el mensaje **sin argumentos** y el campo `pragma.error`, y se notifica a `LogAlertListener.onInternalError` (ALR-05).
- Con masking fallido se emite `"[unmaskable]"`, nunca el valor original.
- Los logs internos de la librería usan un logger con prefijo `co.com.pragma.logging.internal` que el encoder no procesa (evita reentrada).
- No se empaqueta ningún `logback.xml` (INT-06). El core no trae binding de SLF4J (INT-07).
