package co.com.pragma.logging.core.enrich;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Runs the configured enrichers and those registered in code (ENR-07). Thread-safe. */
public final class LogEnrichers {

    private static final List<LogEnricher> REGISTERED = new CopyOnWriteArrayList<>();
    private static final ThreadLocal<Boolean> RUNNING = new ThreadLocal<>();
    private static final LogEnrichers NONE = new LogEnrichers(List.of());

    private final List<LogEnricher> configured;

    /** @param configured enrichers from configuration */
    public LogEnrichers(List<LogEnricher> configured) {
        this.configured = List.copyOf(configured);
    }

    /** Only enrichers registered in code. */
    public static LogEnrichers none() {
        return NONE;
    }

    /** Adds an enricher for every pragma-log encoder and formatter of this class loader. */
    public static void register(LogEnricher enricher) {
        REGISTERED.add(enricher);
    }

    public static void unregister(LogEnricher enricher) {
        REGISTERED.remove(enricher);
    }

    /**
     * Creates an enricher from its class name.
     *
     * @throws IllegalArgumentException when it cannot be loaded or is not a {@link LogEnricher}
     */
    public static LogEnricher load(String className, ClassLoader classLoader) {
        try {
            Class<?> type = Class.forName(className.trim(), true, classLoader);
            return (LogEnricher) type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
            throw new IllegalArgumentException("pragma.log.enrichers: cannot load " + className
                    + " (it must implement LogEnricher and have a public no-arg constructor)", e);
        }
    }

    /** Fields the enrichers add to an event, in order; empty when there are none. */
    public Map<String, Object> fields(String logger, String level) {
        if ((configured.isEmpty() && REGISTERED.isEmpty()) || RUNNING.get() != null) {
            return Map.of();
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        LogEnricher.EnrichedEvent event = new LogEnricher.EnrichedEvent() {
            @Override
            public String logger() {
                return logger;
            }

            @Override
            public String level() {
                return level;
            }

            @Override
            public void put(String name, Object value) {
                if (name != null && value != null) {
                    fields.put(name, value);
                }
            }
        };
        RUNNING.set(Boolean.TRUE);
        try {
            configured.forEach(enricher -> safely(enricher, event));
            REGISTERED.forEach(enricher -> safely(enricher, event));
        } finally {
            RUNNING.remove();
        }
        return fields;
    }

    private static void safely(LogEnricher enricher, LogEnricher.EnrichedEvent event) {
        try {
            enricher.enrich(event);
        } catch (RuntimeException | LinkageError e) {
            // An enricher must never break logging (ADR-15).
        }
    }
}
