package co.com.pragma.logging.core.enrich;

/**
 * Adds fields to every log event (ENR-07), e.g. the tenant of the current user or the region of the pod.
 *
 * <pre>{@code
 * public class TenantEnricher implements LogEnricher {
 *     public void enrich(EnrichedEvent event) {
 *         event.put("tenantId", TenantContext.current());
 *     }
 * }
 * }</pre>
 *
 * <p>Register it with {@code pragma.log.enrichers} (class name, public no-arg constructor), with
 * {@link LogEnrichers#register(LogEnricher)}, or, in Spring Boot, as a bean. The fields are masked like key-values
 * (by name and by pattern) and can be hidden, renamed or ordered like any other. A key-value or MDC entry with the
 * same name wins.
 *
 * <p>It runs on the logging thread for every event: keep it fast. An exception thrown here is ignored, and logs
 * written from an enricher are not enriched.
 */
@FunctionalInterface
public interface LogEnricher {

    void enrich(EnrichedEvent event);

    /** The event being enriched. */
    interface EnrichedEvent {

        String logger();

        String level();

        /** Adds a field; {@code null} values are ignored. */
        void put(String name, Object value);
    }
}
