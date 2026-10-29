# pragma-log — Rendimiento (PERF-01)

Benchmarks JMH en [`benchmarks/`](../../benchmarks/src/jmh/java/co/com/pragma/logging/benchmarks/EncoderBenchmark.java). Miden **solo el encoding** de un evento (sin I/O): el costo que la librería agrega por log.

```bash
./gradlew :benchmarks:jmh                                   # corrida completa
./gradlew :benchmarks:jmh -PjmhArgs="-wi 1 -i 2 -f 1 Masked" # rápida, filtrando por nombre
```

## Línea base — 2026-10-29 (commit de E2)

Apple M4, OpenJDK 21.0.8, `-wi 3 -i 5 -w 1s -r 1s -f 1`. Tiempo promedio por evento (menos es mejor).

| Escenario | logstash-logback-encoder | PragmaJsonEncoder | Factor |
|---|---|---|---|
| Mensaje simple + MDC | 383 ns | 565 ns | 1,5× |
| Objeto en `{}` (record con 4 campos) | 454 ns ¹ | 1.343 ns | 3,0× |
| Objeto como key-value | — | 1.474 ns | — |

¹ La línea base no es comparable en seguridad: logstash usa `toString()` del objeto y **no enmascara nada**. Pragma lo serializa con Jackson y aplica masking.

## Lectura

- El costo fijo de Pragma (≈ +180 ns por evento) viene de envolver el evento, revisar nombres sensibles en el MDC y escribir los campos fijos.
- Serializar y enmascarar un objeto cuesta ≈ +800 ns. Es el precio de MSK-07: no hay forma segura de loguear un objeto sin recorrerlo.
- Como referencia, 1,5 µs por log equivale a ~0,15 % de CPU para un servicio que escribe 1.000 logs por segundo.

## Pendiente

- Objetivo formal de PERF-01 (propuesta: mensaje simple ≤ 2× la línea base; hoy 1,5×).
- Medir en CI de forma comparativa (no absoluta) para detectar regresiones.
- Explorar caché de serializadores por clase de argumento si los objetos grandes dominan.
