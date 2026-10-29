package co.com.pragma.logging.spring;

import io.micrometer.context.ThreadLocalAccessor;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.MDC;

/**
 * Bridges the request context stored in the Reactor {@code Context} to the MDC of whichever thread runs
 * each reactive operator (ADR-10, CTX-05). Registered through {@code META-INF/services}; only the
 * {@link RequestLogContext} keys are written or cleared, other MDC entries are left alone.
 */
public class PragmaMdcThreadLocalAccessor implements ThreadLocalAccessor<Map<String, String>> {

    /** Reactor Context key under which the request context travels. */
    public static final String KEY = "pragma.log.requestContext";

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public Map<String, String> getValue() {
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        if (mdc == null) {
            return null;
        }
        Map<String, String> context = new HashMap<>();
        mdc.forEach((key, value) -> {
            if (RequestLogContext.isContextKey(key)) {
                context.put(key, value);
            }
        });
        return context.isEmpty() ? null : context;
    }

    @Override
    public void setValue(Map<String, String> value) {
        clearContextKeys();
        value.forEach(MDC::put);
    }

    @Override
    public void setValue() {
        clearContextKeys();
    }

    @Override
    public void restore(Map<String, String> previousValue) {
        setValue(previousValue);
    }

    @Override
    public void restore() {
        clearContextKeys();
    }

    private static void clearContextKeys() {
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        if (mdc != null) {
            mdc.keySet().stream().filter(RequestLogContext::isContextKey).forEach(MDC::remove);
        }
    }
}
