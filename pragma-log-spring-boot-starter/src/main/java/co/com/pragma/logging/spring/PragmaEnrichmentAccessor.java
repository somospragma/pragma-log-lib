package co.com.pragma.logging.spring;

import io.micrometer.context.ThreadLocalAccessor;
import java.util.Map;
import org.slf4j.MDC;

/**
 * Carries the fields of a {@code @LogEnrichment} call through a Reactor pipeline (ENR-05): they travel in the
 * Reactor {@code Context} and are put in the MDC of whichever thread runs each operator, then removed. Only the
 * keys it put are removed. Registered through {@code META-INF/services}.
 */
public class PragmaEnrichmentAccessor implements ThreadLocalAccessor<Map<String, String>> {

    /** Reactor Context key under which the fields travel. */
    public static final String KEY = "pragma.log.enrichment";

    private static final ThreadLocal<Map<String, String>> CURRENT = new ThreadLocal<>();

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public Map<String, String> getValue() {
        return CURRENT.get();
    }

    @Override
    public void setValue(Map<String, String> value) {
        clear();
        value.forEach(MDC::put);
        CURRENT.set(value);
    }

    @Override
    public void setValue() {
        clear();
    }

    @Override
    public void restore(Map<String, String> previousValue) {
        setValue(previousValue);
    }

    @Override
    public void restore() {
        clear();
    }

    private static void clear() {
        Map<String, String> current = CURRENT.get();
        if (current != null) {
            current.keySet().forEach(MDC::remove);
            CURRENT.remove();
        }
    }
}
